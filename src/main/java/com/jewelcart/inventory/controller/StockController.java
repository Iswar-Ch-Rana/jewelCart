package com.jewelcart.inventory.controller;

import com.jewelcart.common.dto.ApiResponse;
import com.jewelcart.inventory.dto.DeductStockRequest;
import com.jewelcart.inventory.dto.InitializeStockRequest;
import com.jewelcart.inventory.dto.RestockRequest;
import com.jewelcart.inventory.dto.StockResponse;
import com.jewelcart.inventory.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.jewelcart.common.dto.ApiResponse.success;

@RestController
@RequestMapping("/v1/stock")
@RequiredArgsConstructor
@Validated
public class StockController {

    private final StockService stockService;

    @PostMapping
    public ResponseEntity<ApiResponse<StockResponse>> initializeStock(@Valid @RequestBody InitializeStockRequest request) {
        return ResponseEntity.ok(success("Stock initialized successfully", stockService.initializeStock(request)));
    }

    @PostMapping("/restock")
    public ResponseEntity<ApiResponse<StockResponse>> restock(@Valid @RequestBody RestockRequest request) {
        return ResponseEntity.ok(success("Stock restocked successfully", stockService.restock(request)));
    }

    @PostMapping("/deduct")
    public ResponseEntity<ApiResponse<StockResponse>> deductStock(@Valid @RequestBody DeductStockRequest request) {
        return ResponseEntity.ok(success("Stock deducted successfully", stockService.deductStock(request)));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<StockResponse>>> getStockByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(success("Stock retrieved successfully", stockService.getStockByProduct(productId)));
    }

    @GetMapping("/product/{productId}/variant/{variantId}")
    public ResponseEntity<ApiResponse<StockResponse>> getStockByVariant(@PathVariable Long productId, @PathVariable Long variantId) {
        return ResponseEntity.ok(success("Stock retrieved successfully", stockService.getStockByVariant(productId, variantId)));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<StockResponse>>> getLowStockItems() {
        return ResponseEntity.ok(success("Low stock items retrieved successfully", stockService.getLowStockItems()));
    }

}
