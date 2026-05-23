package com.jewelcart.vendor.dto;

import java.time.Instant;

public record VendorResponse(
        Long id,
        String name,
        String brandName,
        String email,
        String gstIn,
        String phone,
        String address,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
