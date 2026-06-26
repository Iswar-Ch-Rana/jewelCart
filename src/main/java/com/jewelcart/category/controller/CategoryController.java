package com.jewelcart.category.controller;

import com.jewelcart.category.dto.CategoryResponse;
import com.jewelcart.category.dto.CategoryTreeResponse;
import com.jewelcart.category.dto.CreateCategoryRequest;
import com.jewelcart.category.dto.UpdateCategoryRequest;
import com.jewelcart.category.service.CategoryService;
import com.jewelcart.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jewelcart.common.dto.ApiResponse.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/categories")
@Tag(name = "Categories", description = "Category management APIs")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Create a new category")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Category created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Parent category not found")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @RequestBody @Valid CreateCategoryRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(success("Category created successfully", categoryService.createCategory(request)));
    }

    @Operation(summary = "Get category by ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Category found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                success("Category retrieved successfully", categoryService.getCategoryById(id)));
    }

    @Operation(summary = "Get all categories as tree", description = "Returns full category hierarchy — root nodes with nested children")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Category tree retrieved")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getAllCategoriesAsTree() {

        return ResponseEntity.ok(
                success("Categories retrieved successfully", categoryService.getAllCategoriesAsTree()));
    }

    @Operation(summary = "Update a category")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Category updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or circular parent reference")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCategoryRequest request) {

        return ResponseEntity.ok(
                success("Category updated successfully", categoryService.updateCategory(id, request)));
    }

    @Operation(summary = "Deactivate a category", description = "Soft delete — sets is_active to false")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Category deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    })
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCategory(
            @PathVariable Long id) {

        categoryService.deactivateCategory(id);
        return ResponseEntity.noContent().build();  // 204 — no body
    }
}