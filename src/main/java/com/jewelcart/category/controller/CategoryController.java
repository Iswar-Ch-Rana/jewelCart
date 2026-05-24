package com.jewelcart.category.controller;

import com.jewelcart.category.dto.CategoryResponse;
import com.jewelcart.category.dto.CategoryTreeResponse;
import com.jewelcart.category.dto.CreateCategoryRequest;
import com.jewelcart.category.dto.UpdateCategoryRequest;
import com.jewelcart.category.service.CategoryService;
import com.jewelcart.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jewelcart.common.dto.ApiResponse.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @RequestBody @Valid CreateCategoryRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(success("Category created successfully", categoryService.createCategory(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                success("Category retrieved successfully", categoryService.getCategoryById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getAllCategoriesAsTree() {

        return ResponseEntity.ok(
                success("Categories retrieved successfully", categoryService.getAllCategoriesAsTree()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCategoryRequest request) {

        return ResponseEntity.ok(
                success("Category updated successfully", categoryService.updateCategory(id, request)));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateCategory(
            @PathVariable Long id) {

        categoryService.deactivateCategory(id);
        return ResponseEntity.noContent().build();  // 204 — no body
    }
}