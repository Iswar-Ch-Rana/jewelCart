package com.jewelcart.order.service;

import com.jewelcart.auth.entity.User;
import com.jewelcart.common.enums.OrderStatus;
import com.jewelcart.common.exception.ResourceNotFoundException;
import com.jewelcart.inventory.dto.DeductStockRequest;
import com.jewelcart.inventory.dto.RestockRequest;
import com.jewelcart.inventory.service.StockService;
import com.jewelcart.order.dto.*;
import com.jewelcart.order.entity.Order;
import com.jewelcart.order.entity.OrderItem;
import com.jewelcart.order.repository.OrderRepository;
import com.jewelcart.product.entity.Product;
import com.jewelcart.product.entity.ProductImage;
import com.jewelcart.product.entity.ProductVariant;
import com.jewelcart.product.repository.ProductRepository;
import com.jewelcart.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.jewelcart.common.util.SecurityUtils.getCurrentUser;

/**
 * OrderService — Core order placement logic
 * <p>
 * Key patterns used:
 * - 4-phase placeOrder  → minimize lock duration, prevent deadlock
 * - Pessimistic locking → prevent overselling (see StockService)
 * - State machine       → validateTransition() enforces valid status flow
 * - Bulk loading        → findAllById() prevents N+1 in order placement
 * <p>
 * See: docs/06-order-deep-dive.md for full explanation
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final StockService stockService;

    // ─── PLACE ORDER ──────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        User currentUser = getCurrentUser();

        // ── PHASE 1: Sort + Load all data (zero locks held) ───────────────────
        //
        // WHY SORT: always lock in ascending productId order
        //   prevents deadlock — two threads can never wait for each other
        //   Thread 1 locks product 1 then 2; Thread 2 also locks 1 then 2
        //   no circular dependency → no deadlock
        List<OrderItemRequest> sortedItems = request.items().stream()
                .sorted(Comparator.comparing(OrderItemRequest::productId))
                .toList();

        // WHY BULK LOAD: avoid N+1 — 30 items = 2 queries, not 60
        //   findAllById() generates: WHERE id IN (1,2,3...30)
        List<Long> productIds = sortedItems.stream()
                .map(OrderItemRequest::productId)
                .toList();

        List<Long> variantIds = sortedItems.stream()
                .map(OrderItemRequest::variantId)
                .filter(Objects::nonNull)   // skip null variantIds
                .toList();

        // ONE query for all products
        Map<Long, Product> productMap = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // ONE query for all variants (skip entirely if no variants in order)
        Map<Long, ProductVariant> variantMap = variantIds.isEmpty()
                ? new HashMap<>()
                : variantRepository.findAllById(variantIds)
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        // WHY VALIDATE BEFORE LOCKING: fail fast
        //   if product doesn't exist → throw before acquiring any locks
        //   no cleanup needed — nothing was locked yet
        for (OrderItemRequest item : sortedItems) {
            if (!productMap.containsKey(item.productId())) {
                throw new ResourceNotFoundException("Product not found: " + item.productId());
            }
            // only check variant if one was requested
            if (item.variantId() != null && !variantMap.containsKey(item.variantId())) {
                throw new ResourceNotFoundException("Variant not found: " + item.variantId());
            }
        }

        // ── PHASE 2: Deduct stock (locks held briefly per item) ───────────────
        //
        // WHY SEPARATE FROM PHASE 1:
        //   all validation done → only lock if we know everything is valid
        //   locks acquired in sorted productId order → no deadlocks
        //
        // PROPAGATION NOTE:
        //   deductStock joins THIS transaction (PROPAGATION.REQUIRED default)
        //   locks technically held until placeOrder commits
        //   acceptable at Phase 1 scale
        //   TODO Phase 5: move to Kafka async → no locks during HTTP request
        for (OrderItemRequest item : sortedItems) {
            stockService.deductStock(new DeductStockRequest(
                    item.productId(),
                    item.variantId(),
                    item.quantity()
            ));
        }

        // ── PHASE 3: Build order items (pure Java, zero DB calls) ─────────────
        //
        // WHY NO DB CALLS HERE:
        //   all data already loaded in Phase 1 → productMap, variantMap
        //   calculation is pure math — no reason to hit the database
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalGst = BigDecimal.ZERO;

        for (OrderItemRequest item : sortedItems) {
            Product product = productMap.get(item.productId());
            ProductVariant variant = item.variantId() != null
                    ? variantMap.get(item.variantId())
                    : null;  // null = product has no variant

            // price = base selling price + variant additional price (if any)
            BigDecimal unitPrice = product.getSellingPrice();
            if (variant != null) {
                unitPrice = unitPrice.add(variant.getAdditionalPrice());
            }

            BigDecimal itemSubtotal = unitPrice
                    .multiply(BigDecimal.valueOf(item.quantity()));

            // WHY RoundingMode.HALF_UP:
            //   standard financial rounding — 0.5 rounds up
            //   without rounding mode → ArithmeticException for 1/3 etc.
            BigDecimal gstAmount = itemSubtotal
                    .multiply(product.getGstRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal totalPrice = itemSubtotal.add(gstAmount);

            orderItems.add(OrderItem.builder()
                    .product(product)
                    .variant(variant)
                    .quantity(item.quantity())
                    .unitPrice(unitPrice)
                    .gstRate(product.getGstRate())
                    .gstAmount(gstAmount)
                    .totalPrice(totalPrice)
                    .build());

            subtotal = subtotal.add(itemSubtotal);
            totalGst = totalGst.add(gstAmount);
        }

        // ── PHASE 4: Save order (single DB write) ─────────────────────────────
        //
        // WHY ONE SAVE:
        //   CascadeType.ALL on Order.items
        //   saving Order automatically saves all OrderItems
        //   one INSERT for order + N INSERTs for items in one transaction
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .user(currentUser)
                .status(OrderStatus.PENDING)
                .subtotal(subtotal)
                .gstAmount(totalGst)
                .totalAmount(subtotal.add(totalGst))
                .shippingAddress(request.shippingAddress())
                .notes(request.notes())
                .build();

        // link each item back to the order before cascade save
        orderItems.forEach(item -> item.setOrder(order));
        order.getItems().addAll(orderItems);

        return toResponse(orderRepository.save(order));
    }

    // ─── READ OPERATIONS ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        // findByIdWithItems uses @EntityGraph — loads items + products
        // in separate queries, no Cartesian product / MultipleBagFetchException
        return orderRepository.findByIdWithItems(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDTO> getMyOrders(Pageable pageable) {
        // SecurityContextHolder → user set by JwtAuthFilter on every request
        // no extra DB call — user already loaded during JWT validation
        Long userId = getCurrentUser().getId();
        return orderRepository.findByUserId(userId, pageable)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDTO> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable)
                .map(this::toSummary);
    }

    // ─── UPDATE STATUS ────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        // state machine validation — throws if transition is invalid
        validateTransition(order.getStatus(), request.status());

        order.setStatus(request.status());
        return toResponse(order);   // dirty checking saves automatically
    }

    // ─── CANCEL ORDER ─────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        // state machine: only PENDING or CONFIRMED can be cancelled
        validateTransition(order.getStatus(), OrderStatus.CANCELLED);

        // WHY RESTORE STOCK:
        //   cancelled order = items back in inventory
        //   both stock restore + status update in same @Transactional
        //   if either fails → both roll back → data stays consistent
        order.getItems().forEach(item ->
                stockService.restock(new RestockRequest(
                        item.getProduct().getId(),
                        item.getVariant() != null ? item.getVariant().getId() : null,
                        item.getQuantity()
                ))
        );

        order.setStatus(OrderStatus.CANCELLED);
        return toResponse(order);
    }

    // ─── STATE MACHINE ────────────────────────────────────────────────────────
    //
    // Valid transitions:
    //   PENDING   → CONFIRMED, CANCELLED
    //   CONFIRMED → PROCESSING, CANCELLED
    //   PROCESSING → SHIPPED
    //   SHIPPED   → DELIVERED
    //   DELIVERED, CANCELLED, REFUNDED → terminal (no further transitions)
    //
    // WHY STATE MACHINE:
    //   without it → anyone can set any status (PENDING → DELIVERED directly)
    //   enforces business rules at application level
    //   throws IllegalStateException for invalid transitions → 409 CONFLICT
    private void validateTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PROCESSING || next == OrderStatus.CANCELLED;
            case PROCESSING -> next == OrderStatus.SHIPPED;
            case SHIPPED -> next == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED, REFUNDED -> false;
        };

        if (!valid) {
            throw new IllegalStateException("Invalid order transition: " + current + " → " + next);
        }
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    // WHY DATE + MILLIS:
    //   human readable (ORD-20240601-7890)
    //   sortable by date
    //   practically unique at Phase 1 scale
    //   TODO Phase 5: replace with DB sequence for guaranteed uniqueness
    private String generateOrderNumber() {
        String datePart = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String msPart = String.valueOf(System.currentTimeMillis()).substring(9);
        return "ORD-" + datePart + "-" + msPart;
    }

    // full detail response — used for single order API
    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getSubtotal(),
                order.getGstAmount(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getNotes(),
                order.getItems().stream()
                        .map(item -> new OrderItemResponse(
                                item.getProduct().getId(),
                                item.getProduct().getName(),
                                // get primary image — filter in Java
                                // Phase 2: replace it with Redis cached primary image
                                item.getProduct().getImages().stream()
                                        .filter(ProductImage::getIsPrimary)
                                        .map(ProductImage::getImageUrl)
                                        .findFirst()
                                        .orElse(null),
                                item.getVariant() != null ? item.getVariant().getId() : null,
                                item.getVariant() != null ? item.getVariant().getVariantName() : null,
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getGstRate(),
                                item.getGstAmount(),
                                item.getTotalPrice()
                        )).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    // lightweight summary — used for order list API
    private OrderSummaryDTO toSummary(Order order) {
        return new OrderSummaryDTO(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems().size(),    // item count without loading items
                order.getCreatedAt()
        );
    }

}
