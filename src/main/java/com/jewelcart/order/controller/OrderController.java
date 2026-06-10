package com.jewelcart.order.controller;

import com.jewelcart.common.dto.ApiResponse;
import com.jewelcart.common.enums.OrderStatus;
import com.jewelcart.order.dto.OrderResponse;
import com.jewelcart.order.dto.OrderSummaryDTO;
import com.jewelcart.order.dto.PlaceOrderRequest;
import com.jewelcart.order.dto.UpdateOrderStatusRequest;
import com.jewelcart.order.service.OrderService;
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
public class OrderController {

    private final OrderService orderService;

    // POST /v1/orders — any authenticated user can place order
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success("Order placed successfully", orderService.placeOrder(request)));
    }

    // GET /v1/orders/{id} — admin sees any order, customer sees own
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(success("Order retrieved successfully", orderService.getOrderById(id)));
    }

    // GET /v1/orders/my-orders — customer sees their own orders
    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<Page<OrderSummaryDTO>>> getMyOrders(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Orders retrieved successfully", orderService.getMyOrders(pageable)));
    }

    // GET /v1/orders/status/PENDING — admin filters by status
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<OrderSummaryDTO>>> getOrdersByStatus(@PathVariable OrderStatus status, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Orders retrieved successfully", orderService.getOrdersByStatus(status, pageable)));
    }

    // PATCH /v1/orders/{id}/status — admin updates order status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(success("Order status updated successfully", orderService.updateOrderStatus(id, request)));
    }

    // PATCH /v1/orders/{id}/cancel — customer or admin cancels order
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(success("Order cancelled successfully", orderService.cancelOrder(id)));
    }

}
