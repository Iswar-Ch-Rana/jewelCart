package com.jewelcart.category.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank(message = "Name is required")
        String name,
        Long parentId,        // optional — null means root category
        String description,
        String imageUrl,
        Integer displayOrder
) {
}
