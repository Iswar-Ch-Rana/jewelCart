package com.jewelcart.inventory.service;

import com.jewelcart.common.exception.DuplicateResourceException;
import com.jewelcart.common.exception.InsufficientStockException;
import com.jewelcart.common.exception.ResourceNotFoundException;
import com.jewelcart.inventory.dto.DeductStockRequest;
import com.jewelcart.inventory.dto.InitializeStockRequest;
import com.jewelcart.inventory.dto.RestockRequest;
import com.jewelcart.inventory.dto.StockResponse;
import com.jewelcart.inventory.entity.Stock;
import com.jewelcart.inventory.repository.StockRepository;
import com.jewelcart.product.entity.Product;
import com.jewelcart.product.entity.ProductVariant;
import com.jewelcart.product.repository.ProductRepository;
import com.jewelcart.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    // ─── WRITE OPERATIONS ────────────────────────────────────────────────────

    @Transactional
    public StockResponse initializeStock(InitializeStockRequest request) {
        // prevent duplicate stock entry for same product+variant combination
        if (stockRepository.findByProductIdAndVariantId(request.productId(), request.variantId()).isPresent()) {
            throw new DuplicateResourceException("Stock already exists for product: " + request.productId());
        }

        // validate product exists
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.productId()));

        // variant is optional — null means no variant (base product stock)
        ProductVariant variant = null;
        if (request.variantId() != null) {
            variant = variantRepository.findById(request.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + request.variantId()));
        }

        Stock stock = Stock.builder()
                .product(product)
                .variant(variant)
                .quantity(request.quantity())
                .lowStockThreshold(request.lowStockThreshold())
                .updatedAt(Instant.now())   // manual — no BaseEntity
                .build();

        return toResponse(stockRepository.save(stock));
    }

    @Transactional
    public StockResponse restock(RestockRequest request) {
        // find existing stock — no lock needed for restock (vendor operation, not concurrent)
        Stock stock = stockRepository.findByProductIdAndVariantId(request.productId(), request.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found for product: " + request.productId()));

        stock.setQuantity(stock.getQuantity() + request.quantity());
        stock.setUpdatedAt(Instant.now());

        return toResponse(stock);   // dirty checking saves automatically
    }

    @Transactional
    public StockResponse deductStock(DeductStockRequest request) {
        // acquire pessimistic lock BEFORE reading quantity
        // prevents race condition — two threads can't deduct simultaneously
        Stock stock = stockRepository.findByProductAndVariantWithLock(request.productId(), request.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found for product: " + request.productId()));

        // check quantity AFTER acquiring lock — safe from race condition now
        if (stock.getQuantity() < request.quantity()) {
            throw new InsufficientStockException("Insufficient stock for product: " + request.productId() + ". Available: " + stock.getQuantity() + ", Requested: " + request.quantity());
        }

        stock.setQuantity(stock.getQuantity() - request.quantity());
        stock.setUpdatedAt(Instant.now());

        return toResponse(stock);   // dirty checking saves automatically
    }

    // ─── READ OPERATIONS ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StockResponse> getStockByProduct(Long productId) {
        return stockRepository.findByProductId(productId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StockResponse getStockByVariant(Long productId, Long variantId) {
        return stockRepository.findByProductIdAndVariantId(productId, variantId).map(this::toResponse).orElseThrow(() -> new ResourceNotFoundException("Stock not found for product: " + productId));
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getLowStockItems() {
        return stockRepository.findLowStockItems().stream().map(this::toResponse).toList();
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    private StockResponse toResponse(Stock stock) {
        return new StockResponse(
                stock.getId(),
                stock.getProduct().getId(),
                stock.getProduct().getName(),
                stock.getVariant() != null ? stock.getVariant().getId() : null,
                stock.getQuantity(),
                stock.getLowStockThreshold(),
                // computed — business logic stays on server side
                stock.getQuantity() <= stock.getLowStockThreshold(),
                stock.getUpdatedAt());
    }
}