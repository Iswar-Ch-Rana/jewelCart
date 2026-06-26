package com.jewelcart.order.controller;

import com.jewelcart.common.dto.ApiResponse;
import com.jewelcart.common.enums.OrderStatus;
import com.jewelcart.order.dto.OrderResponse;
import com.jewelcart.order.dto.OrderSummaryDTO;
import com.jewelcart.order.dto.PlaceOrderRequest;
import com.jewelcart.order.dto.UpdateOrderStatusRequest;
import com.jewelcart.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.jewelcart.common.dto.ApiResponse.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/orders")
@Validated
@Tag(name = "Orders", description = "Order placement and management APIs")
public class OrderController {

    private final OrderService orderService;

    // POST /v1/orders — any authenticated user can place order
    @Operation(summary = "Place a new order", description = "Creates an order from the user's cart. Deducts stock with pessimistic locking to prevent oversell.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Order placed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or insufficient stock"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product or variant not found")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success("Order placed successfully", orderService.placeOrder(request)));
    }

    // GET /v1/orders/{id} — admin sees any order, customer sees own
    @Operation(summary = "Get order by ID", description = "Admin can fetch any order. Customer can only fetch their own order.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied — not your order"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(success("Order retrieved successfully", orderService.getOrderById(id)));
    }

    // GET /v1/orders/my-orders — customer sees their own orders
    @Operation(summary = "Get current user's orders", description = "Returns paginated order history for the authenticated user, sorted by created_at DESC")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Orders retrieved")
    })
    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<Page<OrderSummaryDTO>>> getMyOrders(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Orders retrieved successfully", orderService.getMyOrders(pageable)));
    }

    // GET /v1/orders/status/PENDING — admin filters by status
    @Operation(summary = "Get orders by status", description = "Admin-only. Filter all orders by status (PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, REFUNDED)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Orders retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status value")
    })
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<OrderSummaryDTO>>> getOrdersByStatus(@PathVariable OrderStatus status, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Orders retrieved successfully", orderService.getOrdersByStatus(status, pageable)));
    }

    // PATCH /v1/orders/{id}/status — admin updates order status
    @Operation(summary = "Update order status", description = "Admin-only. Follows state machine: PENDING→CONFIRMED→PROCESSING→SHIPPED→DELIVERED. Invalid transitions are rejected.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(success("Order status updated successfully", orderService.updateOrderStatus(id, request)));
    }

    // PATCH /v1/orders/{id}/cancel — customer or admin cancels order
    @Operation(summary = "Cancel an order", description = "Customer can cancel their own order. Admin can cancel any order. Only PENDING or CONFIRMED orders can be cancelled.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Order cannot be cancelled in its current state"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied — not your order"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(success("Order cancelled successfully", orderService.cancelOrder(id)));
    }

}
