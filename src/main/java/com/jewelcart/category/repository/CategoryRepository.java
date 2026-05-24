package com.jewelcart.category.repository;

import com.jewelcart.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // load all active categories — for tree building
    List<Category> findByIsActiveTrue();

    // load root categories only (no parent)
    List<Category> findByParentIsNull();

    // load children of a specific parent
    List<Category> findByParentId(Long parentId);
}
