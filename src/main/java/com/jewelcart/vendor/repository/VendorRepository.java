package com.jewelcart.vendor.repository;

import com.jewelcart.vendor.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    // derived query — Spring generates: SELECT * FROM vendors WHERE email = ?
    Optional<Vendor> findByEmail(String email);

    // pagination — returns vendors where is_active = true with page/size support
    Page<Vendor> findByIsActiveTrue(Pageable pageable);
}