package com.jewelcart.order.repository;

import com.jewelcart.common.enums.OrderStatus;
import com.jewelcart.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // customer sees their own orders — paginated
    Page<Order> findByUserId(Long userId, Pageable pageable);

    // admin filters orders by status — paginated
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    // find by order number — for order lookup
    Optional<Order> findByOrderNumber(String orderNumber);

    // WITHOUT @EntityGraph — dangerous for order detail:
    // Hibernate would use LAZY loading
    // Accessing order.getItems() → Query 1: SELECT * FROM order_items WHERE order_id=?
    // Then for EACH item accessing item.getProduct() → N more queries
    // 1 order + 3 items = 4 queries minimum → N+1 problem
    //
    // WITH @EntityGraph — controlled fetching:
    // Hibernate runs 3 clean separate queries:
    //   Query 1: SELECT * FROM orders WHERE id=?
    //   Query 2: SELECT * FROM order_items WHERE order_id IN (?)
    //   Query 3: SELECT * FROM products WHERE id IN (1,2,3)
    //   Query 4: SELECT * FROM product_images WHERE product_id IN (1,2,3)
    // No Cartesian product. No duplicate rows. No N+1.
    // attributePaths tells Hibernate exactly which associations to pre-load:
    //   "items"               → load order_items for this order
    //   "items.product"       → load products for those items
    //   "items.product.images"→ load images for those products (primary image filter in Java)
    @EntityGraph(attributePaths = {"items", "items.product", "items.product.images"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);
}