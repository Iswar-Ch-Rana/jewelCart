package com.jewelcart.category.service;

import com.jewelcart.category.dto.CategoryResponse;
import com.jewelcart.category.dto.CategoryTreeResponse;
import com.jewelcart.category.dto.CreateCategoryRequest;
import com.jewelcart.category.dto.UpdateCategoryRequest;
import com.jewelcart.category.entity.Category;
import com.jewelcart.category.repository.CategoryRepository;
import com.jewelcart.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found with id: " + request.parentId()
                    ));
        }

        Category category = Category.builder()
                .name(request.name())
                .parent(parent)
                .description(request.description())
                .imageUrl(request.imageUrl())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build();

        category = categoryRepository.save(category);
        return toResponse(category);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id
                ));
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getAllCategoriesAsTree() {
        List<Category> all = categoryRepository.findByIsActiveTrue();

        // Step 1: convert all categories to tree nodes — mutable children list
        Map<Long, CategoryTreeResponse> nodeMap = new HashMap<>();
        for (Category cat : all) {
            nodeMap.put(cat.getId(), new CategoryTreeResponse(
                    cat.getId(),
                    cat.getName(),
                    cat.getDisplayOrder(),
                    cat.getIsActive(),
                    new ArrayList<>()   // mutable — children added in step 2
            ));
        }

        // Step 2: assign each node to its parent's children list
        List<CategoryTreeResponse> roots = new ArrayList<>();
        for (Category cat : all) {
            if (cat.getParent() == null) {
                roots.add(nodeMap.get(cat.getId()));        // no parent = root
            } else {
                CategoryTreeResponse parentNode = nodeMap.get(
                        cat.getParent().getId()
                );
                if (parentNode != null) {
                    parentNode.children().add(nodeMap.get(cat.getId())); // attach to parent
                }
            }
        }

        return roots;
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id
                ));

        if (request.parentId() != null) {
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found with id: " + request.parentId()
                    ));
            category.setParent(parent);
        } else {
            category.setParent(null);   // making it a root category
        }

        category.setName(request.name());
        category.setDescription(request.description());
        category.setImageUrl(request.imageUrl());
        category.setDisplayOrder(request.displayOrder());
        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }

        return toResponse(category);    // dirty checking saves automatically
    }

    @Transactional
    public void deactivateCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id
                ));
        category.setIsActive(false);    // soft delete
    }

    // convert entity → flat response DTO
    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getParent() != null ? category.getParent().getId() : null,
                category.getParent() != null ? category.getParent().getName() : null,
                category.getDescription(),
                category.getImageUrl(),
                category.getDisplayOrder(),
                category.getIsActive(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getCreatedBy(),
                category.getUpdatedBy()
        );
    }
}