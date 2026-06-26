package com.jewelcart.product.controller;

import com.jewelcart.common.dto.ApiResponse;
import com.jewelcart.common.enums.GenderType;
import com.jewelcart.common.enums.MetalType;
import com.jewelcart.common.enums.OccasionType;
import com.jewelcart.product.dto.*;
import com.jewelcart.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Products", description = "Product catalog management APIs")
public class ProductController {

    private final ProductService productService;

    // POST /v1/products → 201 Created
    @Operation(summary = "Create a new product")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Product created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Vendor or category not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "SKU already exists")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')") // admins and vendors can create products
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success("Product created successfully", productService.createProduct(request)));
    }

    // GET /v1/products/{id}
    @Operation(summary = "Get product by ID", description = "Returns full product details including images and variants")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(success("Product retrieved successfully", productService.getProductById(id)));
    }

    // GET /v1/products/vendor/{vendorId}?page=0&size=10
    @Operation(summary = "Get products by vendor", description = "Paginated list of active products for a vendor")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Products retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> getProductsByVendor(@PathVariable Long vendorId, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Products retrieved successfully", productService.getProductsByVendor(vendorId, pageable)));
    }

    // GET /v1/products/category/{categoryId}?page=0&size=10
    @Operation(summary = "Get products by category", description = "Paginated list of active products in a category")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Products retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    })
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> getProductsByCategory(@PathVariable Long categoryId, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Products retrieved successfully", productService.getProductsByCategory(categoryId, pageable)));
    }

    // GET /v1/products/search?name=ring&page=0&size=10
    @Operation(summary = "Search products by name", description = "Case-insensitive LIKE search on product name — uses JPQL LIKE query")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search results retrieved")
    })
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> searchByName(@RequestParam String name, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Search results retrieved successfully", productService.searchProductsByName(name, pageable)));
    }

    // GET /v1/products/featured?page=0&size=10
    @Operation(summary = "Get featured products", description = "Paginated list of products marked as is_featured = true")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Featured products retrieved")
    })
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> getFeaturedProducts(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Featured products retrieved successfully", productService.getFeaturedProducts(pageable)));
    }

    // GET /v1/products/recent?limit=10
    @Operation(summary = "Get recently added products", description = "Returns latest N active products ordered by created_at DESC. Max 50.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recent products retrieved")
    })
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<ProductSummaryDTO>>> getRecentProducts(@RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(success("Recent products retrieved successfully", productService.getRecentProducts(limit)));
    }

    // GET /v1/products/filter?metalType=GOLD_PLATED&minPrice=1000&maxPrice=50000
    @Operation(summary = "Filter products by metal type and price range")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Filtered products retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid metal type or price range")
    })
    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> filterByMetalAndPrice(@RequestParam MetalType metalType, @RequestParam BigDecimal minPrice, @RequestParam BigDecimal maxPrice, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Filtered products retrieved successfully", productService.getProductsByMetalTypeAndPriceRange(metalType, minPrice, maxPrice, pageable)));
    }

    // GET /v1/products/occasion?occasion=WEDDING&gender=WOMEN
    @Operation(summary = "Get products by occasion and gender")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Products retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid occasion or gender value")
    })
    @GetMapping("/occasion")
    public ResponseEntity<ApiResponse<Page<ProductSummaryDTO>>> getByOccasionAndGender(@RequestParam OccasionType occasion, @RequestParam GenderType gender, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(success("Products retrieved successfully", productService.getProductsByOccasionAndGender(occasion, gender, pageable)));
    }

    // GET /v1/products/vendor-counts
    @Operation(summary = "Get product count per vendor", description = "Aggregation query — returns each vendor's active product count")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vendor product counts retrieved")
    })
    @GetMapping("/vendor-counts")
    public ResponseEntity<ApiResponse<List<VendorProductCount>>> getVendorProductCounts() {
        return ResponseEntity.ok(success("Vendor product counts retrieved successfully", productService.getVendorProductCounts()));
    }

    // PUT /v1/products/{id}
    @Operation(summary = "Update a product")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "SKU already in use")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(success("Product updated successfully", productService.updateProduct(id, request)));
    }

    // PATCH /v1/products/{id}/deactivate → 204 No Content
    @Operation(summary = "Deactivate a product", description = "Soft delete — sets is_active to false. Only ADMIN can deactivate.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Product deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found")
    })
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateProduct(@PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }
}
