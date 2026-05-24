package com.jewelcart.category.dto;

import java.time.Instant;

public record CategoryResponse(
        Long id,
        String name,
        Long parentId,        // null if root category
        String parentName,    // null if root category
        String description,
        String imageUrl,
        Integer displayOrder,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
}
