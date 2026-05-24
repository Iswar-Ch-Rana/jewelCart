package com.jewelcart.category.dto;

import java.util.List;

public record CategoryTreeResponse(
        Long id,
        String name,
        Integer displayOrder,
        Boolean isActive,
        List<CategoryTreeResponse> children   // recursive — same type
) {
}
