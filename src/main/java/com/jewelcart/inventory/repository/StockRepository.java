package com.jewelcart.inventory.repository;

import com.jewelcart.inventory.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    // all stock entries for a product (one per variant + one base)
    List<Stock> findByProductId(Long productId);

    // find specific variant stock — variantId null = base product stock
    Optional<Stock> findByProductIdAndVariantId(Long productId, Long variantId);

    // pessimistic write lock — blocks other transactions until this one commits
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.product.id = :productId" +
            " AND (s.variant.id = :variantId OR (:variantId IS NULL AND s.variant IS NULL))")
    Optional<Stock> findByProductAndVariantWithLock(
            @Param("productId") Long productId,
            @Param("variantId") Long variantId
    );

    // low stock alert — quantity at or below threshold
    @Query("SELECT s FROM Stock s WHERE s.quantity <= s.lowStockThreshold")
    List<Stock> findLowStockItems();
}