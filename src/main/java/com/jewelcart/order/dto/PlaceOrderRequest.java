package com.jewelcart.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlaceOrderRequest(

        @NotEmpty(message = "Order must contain at least one item")
        @Valid  // validates each OrderItemRequest in the list
        List<OrderItemRequest> items,

        @NotBlank(message = "Shipping address is required")
        String shippingAddress,

        String notes    // optional
) {
}
