package com.jewelcart.vendor.service;

import com.jewelcart.common.exception.DuplicateResourceException;
import com.jewelcart.common.exception.ResourceNotFoundException;
import com.jewelcart.vendor.dto.CreateVendorRequest;
import com.jewelcart.vendor.dto.UpdateVendorRequest;
import com.jewelcart.vendor.dto.VendorResponse;
import com.jewelcart.vendor.entity.Vendor;
import com.jewelcart.vendor.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor        // generates constructor for all final fields
public class VendorService {

    private final VendorRepository vendorRepository;

    @Transactional
    public VendorResponse createVendor(CreateVendorRequest request) {
        // explicit check — clear error message
        if (vendorRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateResourceException(
                    "Vendor with email already exists: " + request.email()
            );
        }

        Vendor vendor = Vendor.builder()
                .name(request.name())
                .brandName(request.brandName())
                .gstIn(request.gstIn())
                .email(request.email())
                .phone(request.phone())
                .address(request.address())
                .build();

        try {
            vendor = vendorRepository.save(vendor);
        } catch (DataIntegrityViolationException ex) {
            // safety net for race conditions
            throw new DuplicateResourceException(
                    "Vendor with email already exists: " + request.email()
            );
        }

        return toResponse(vendor);
    }

    @Transactional(readOnly = true)     // no write lock, faster reads
    public Page<VendorResponse> getAllVendors(Pageable pageable) {
        return vendorRepository.findByIsActiveTrue(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VendorResponse getVendorById(Long id) {
        return vendorRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vendor not found with id: " + id
                ));
    }

    @Transactional
    public VendorResponse updateVendor(Long id, UpdateVendorRequest request) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vendor not found with id: " + id
                ));

        vendor.setName(request.name());
        vendor.setBrandName(request.brandName());
        vendor.setGstIn(request.gstIn());
        vendor.setPhone(request.phone());
        vendor.setAddress(request.address());

        // no save() needed — JPA dirty checking detects changes and updates automatically
        return toResponse(vendor);
    }

    @Transactional
    public void deactivateVendor(Long id) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vendor not found with id: " + id
                ));

        vendor.setIsActive(false);      // soft delete — dirty checking saves it
    }

    // convert entity → response DTO (Phase 2: replace with MapStruct)
    private VendorResponse toResponse(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(),
                vendor.getName(),
                vendor.getBrandName(),
                vendor.getEmail(),
                vendor.getGstIn(),
                vendor.getPhone(),
                vendor.getAddress(),
                vendor.getIsActive(),
                vendor.getCreatedAt(),
                vendor.getUpdatedAt()
        );
    }
}