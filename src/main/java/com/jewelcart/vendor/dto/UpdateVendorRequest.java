package com.jewelcart.vendor.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateVendorRequest(

        @NotBlank(message = "Vendor name is required")
        String name,

        @NotBlank(message = "Brand name is required")
        String brandName,

        String gstIn,
        String phone,
        String address
) {
}
