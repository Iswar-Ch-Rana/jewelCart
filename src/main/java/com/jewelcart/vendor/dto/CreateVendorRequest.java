package com.jewelcart.vendor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateVendorRequest(

        @NotBlank(message = "Vendor name is required")
        String name,

        @NotBlank(message = "Brand name is required")
        String brandName,

        @Email(message = "Invalid email format")
        String email,

        String gstIn,
        String phone,
        String address
) {
}
