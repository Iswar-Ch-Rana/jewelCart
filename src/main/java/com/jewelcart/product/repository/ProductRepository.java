package com.jewelcart.product.repository;

import com.jewelcart.common.enums.GenderType;
import com.jewelcart.common.enums.MetalType;
import com.jewelcart.common.enums.OccasionType;
import com.jewelcart.product.dto.VendorProductCount;
import com.jewelcart.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 1. by vendor — paginated
    @Query(
            value = "SELECT p FROM Product p WHERE p.isActive = true AND p.vendor.id = :vendorId",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true AND p.vendor.id = :vendorId"
    )
    Page<Product> findByVendorId(@Param("vendorId") Long vendorId, Pageable pageable);

    // 2. by category — paginated
    @Query(
            value = "SELECT p FROM Product p WHERE p.isActive = true AND p.category.id = :categoryId",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true AND p.category.id = :categoryId"
    )
    Page<Product> findByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    // 3. search by name — case insensitive LIKE
    @Query(
            value = "SELECT p FROM Product p WHERE p.isActive = true AND LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true AND LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))"
    )
    Page<Product> searchByName(@Param("name") String name, Pageable pageable);

    // 4. by metal type and price range
    @Query(
            value = "SELECT p FROM Product p WHERE p.isActive = true AND p.metalType = :metalType " +
                    "AND p.sellingPrice BETWEEN :minPrice AND :maxPrice",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true AND p.metalType = :metalType " +
                    "AND p.sellingPrice BETWEEN :minPrice AND :maxPrice"
    )
    Page<Product> findByMetalTypeAndPriceRange(
            @Param("metalType") MetalType metalType,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );

    // 5. featured products
    @Query(
            value = "SELECT p FROM Product p WHERE p.isActive = true AND p.isFeatured = true",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true AND p.isFeatured = true"
    )
    Page<Product> findFeaturedProducts(Pageable pageable);

    // 6. count per vendor — projection (safer than Object[])
    @Query("SELECT p.vendor.id as vendorId, p.vendor.name as vendorName, " +
            "COUNT(p) as productCount " +
            "FROM Product p WHERE p.isActive = true " +
            "GROUP BY p.vendor.id, p.vendor.name")
    List<VendorProductCount> countActiveProductsPerVendor();

    // 7. with vendor details — JOIN FETCH (no pagination — safe)
    @Query("SELECT p FROM Product p JOIN FETCH p.vendor WHERE p.id = :id AND p.isActive = true")
    Optional<Product> findByIdWithVendor(@Param("id") Long id);

    // 8. by occasion and gender
    @Query(
            value = "SELECT p FROM Product p WHERE p.isActive = true " +
                    "AND p.occasion = :occasion AND p.gender = :gender",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true " +
                    "AND p.occasion = :occasion AND p.gender = :gender"
    )
    Page<Product> findByOccasionAndGender(
            @Param("occasion") OccasionType occasion,
            @Param("gender") GenderType gender,
            Pageable pageable
    );

    // 9. top 10 recent — limit at DB level via Pageable
    @Query("SELECT p FROM Product p WHERE p.isActive = true ORDER BY p.createdAt DESC")
    List<Product> findRecentActiveProducts(Pageable pageable);
}
