package com.jewelcart.vendor.controller;

import com.jewelcart.common.dto.ApiResponse;
import com.jewelcart.vendor.dto.CreateVendorRequest;
import com.jewelcart.vendor.dto.UpdateVendorRequest;
import com.jewelcart.vendor.dto.VendorResponse;
import com.jewelcart.vendor.service.VendorService;
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

import static com.jewelcart.common.dto.ApiResponse.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/vendors")          // /api prefix handled at app level
@Validated                              // enables @Min/@Max on method params
@Tag(name = "Vendors", description = "Vendor management APIs")
public class VendorController {

    private final VendorService vendorService;

    @Operation(summary = "Create a new vendor")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Vendor created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')") // only admins can create vendors
    public ResponseEntity<ApiResponse<VendorResponse>> createVendor(
            @Valid @RequestBody CreateVendorRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(success("Vendor created successfully",
                        vendorService.createVendor(request)));
    }

    @Operation(summary = "Get all vendors", description = "Returns paginated list of all vendors")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vendors retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<VendorResponse>>> getAllVendors(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be 0 or greater")
            int page,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size cannot exceed 100")
            int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                success("Vendors retrieved successfully",
                        vendorService.getAllVendors(pageable)));
    }

    @Operation(summary = "Get vendor by ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vendor found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VendorResponse>> getVendorById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                success("Vendor retrieved successfully",
                        vendorService.getVendorById(id)));
    }

    @Operation(summary = "Update a vendor")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vendor updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Vendor not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already in use")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')") // only admins can update vendors
    public ResponseEntity<ApiResponse<VendorResponse>> updateVendor(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVendorRequest request) {

        return ResponseEntity.ok(
                success("Vendor updated successfully",
                        vendorService.updateVendor(id, request)));
    }

    @Operation(summary = "Deactivate a vendor", description = "Soft delete — sets is_active to false")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Vendor deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')") // only admins can deactivate vendors
    public ResponseEntity<Void> deactivateVendor(
            @PathVariable Long id) {

        vendorService.deactivateVendor(id);
        return ResponseEntity.noContent().build();  // 204 — no body needed
    }
}
