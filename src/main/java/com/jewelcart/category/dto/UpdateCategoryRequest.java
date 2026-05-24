package com.jewelcart.category.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCategoryRequest(
        @NotBlank(message = "Name is required")
        String name,
        Long parentId,
        String description,
        String imageUrl,
        Integer displayOrder,
        Boolean isActive
) {
}
