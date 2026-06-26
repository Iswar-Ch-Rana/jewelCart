package com.jewelcart.inventory.controller;

import com.jewelcart.common.dto.ApiResponse;
import com.jewelcart.inventory.dto.DeductStockRequest;
import com.jewelcart.inventory.dto.InitializeStockRequest;
import com.jewelcart.inventory.dto.RestockRequest;
import com.jewelcart.inventory.dto.StockResponse;
import com.jewelcart.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jewelcart.common.dto.ApiResponse.success;

@RestController
@RequestMapping("/v1/stock")
@RequiredArgsConstructor
@Validated
@Tag(name = "Stock", description = "Inventory and stock management APIs")
public class StockController {

    private final StockService stockService;

    @Operation(summary = "Initialize stock for a product", description = "Creates a stock record for a product or product+variant. Fails if stock already exists.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stock initialized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or stock already exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product or variant not found")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public ResponseEntity<ApiResponse<StockResponse>> initializeStock(@Valid @RequestBody InitializeStockRequest request) {
        return ResponseEntity.ok(success("Stock initialized successfully", stockService.initializeStock(request)));
    }

    @Operation(summary = "Add stock to existing product", description = "Increments quantity for an existing stock record")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stock updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Stock record not found")
    })
    @PostMapping("/restock")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public ResponseEntity<ApiResponse<StockResponse>> restock(@Valid @RequestBody RestockRequest request) {
        return ResponseEntity.ok(success("Stock restocked successfully", stockService.restock(request)));
    }

    @Operation(summary = "Deduct stock", description = "Decrements quantity — uses pessimistic locking to prevent oversell under concurrent orders")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stock deducted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Insufficient stock"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Stock record not found")
    })
    @PostMapping("/deduct")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public ResponseEntity<ApiResponse<StockResponse>> deductStock(@Valid @RequestBody DeductStockRequest request) {
        return ResponseEntity.ok(success("Stock deducted successfully", stockService.deductStock(request)));
    }

    @Operation(summary = "Get stock by product", description = "Returns all stock records for a product — one per variant, plus base product stock if no variants")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stock retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<StockResponse>>> getStockByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(success("Stock retrieved successfully", stockService.getStockByProduct(productId)));
    }

    @Operation(summary = "Get stock for a specific product variant")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stock retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Stock record not found for this product+variant combination")
    })
    @GetMapping("/product/{productId}/variant/{variantId}")
    public ResponseEntity<ApiResponse<StockResponse>> getStockByVariant(@PathVariable Long productId, @PathVariable Long variantId) {
        return ResponseEntity.ok(success("Stock retrieved successfully", stockService.getStockByVariant(productId, variantId)));
    }

    @Operation(summary = "Get low stock items", description = "Returns all stock records where quantity is at or below the low_stock_threshold")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Low stock items retrieved")
    })
    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public ResponseEntity<ApiResponse<List<StockResponse>>> getLowStockItems() {
        return ResponseEntity.ok(success("Low stock items retrieved successfully", stockService.getLowStockItems()));
    }

}
