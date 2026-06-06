package com.jewelcart.product.controller;

import com.jewelcart.common.dto.ApiResponse;
import com.jewelcart.common.enums.GenderType;
import com.jewelcart.common.enums.MetalType;
import com.jewelcart.common.enums.OccasionType;
import com.jewelcart.product.dto.*;
import com.jewelcart.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

import static com.jewelcart.common.dto.ApiResponse.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/products")
@Validated
public class ProductController {

    private final ProductService productService;

    // POST /v1/products → 201 Created
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')") // admins and vendors can create products
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success("Product created successfully", productService.createProduct(request)));
    }

    // GET /v1/products/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(success("Product retrieved successfully", productService.getProductById(id)));
    }

    // GET /v1/products/vendor/{vendorId}?page=0&size=10
    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> getProductsByVendor(@PathVariable Long vendorId, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Products retrieved successfully", productService.getProductsByVendor(vendorId, pageable)));
    }

    // GET /v1/products/category/{categoryId}?page=0&size=10
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> getProductsByCategory(@PathVariable Long categoryId, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Products retrieved successfully", productService.getProductsByCategory(categoryId, pageable)));
    }

    // GET /v1/products/search?name=ring&page=0&size=10
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> searchByName(@RequestParam String name, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Search results retrieved successfully", productService.searchProductsByName(name, pageable)));
    }

    // GET /v1/products/featured?page=0&size=10
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> getFeaturedProducts(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Featured products retrieved successfully", productService.getFeaturedProducts(pageable)));
    }

    // GET /v1/products/recent?limit=10
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<ProductSummaryDTO>>> getRecentProducts(@RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(success("Recent products retrieved successfully", productService.getRecentProducts(limit)));
    }

    // GET /v1/products/filter?metalType=GOLD_PLATED&minPrice=1000&maxPrice=50000
    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> filterByMetalAndPrice(@RequestParam MetalType metalType, @RequestParam BigDecimal minPrice, @RequestParam BigDecimal maxPrice, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Filtered products retrieved successfully", productService.getProductsByMetalTypeAndPriceRange(metalType, minPrice, maxPrice, pageable)));
    }

    // GET /v1/products/occasion?occasion=WEDDING&gender=WOMEN
    @GetMapping("/occasion")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> getByOccasionAndGender(@RequestParam OccasionType occasion, @RequestParam GenderType gender, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Products retrieved successfully", productService.getProductsByOccasionAndGender(occasion, gender, pageable)));
    }

    // GET /v1/products/vendor-counts
    @GetMapping("/vendor-counts")
    public ResponseEntity<ApiResponse<List<VendorProductCount>>> getVendorProductCounts() {
        return ResponseEntity.ok(success("Vendor product counts retrieved successfully", productService.getVendorProductCounts()));
    }

    // PUT /v1/products/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(success("Product updated successfully", productService.updateProduct(id, request)));
    }

    // PATCH /v1/products/{id}/deactivate → 204 No Content
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateProduct(@PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }
}
