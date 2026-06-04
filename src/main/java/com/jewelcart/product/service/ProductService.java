package com.jewelcart.product.service;

import com.jewelcart.category.entity.Category;
import com.jewelcart.category.repository.CategoryRepository;
import com.jewelcart.common.enums.GenderType;
import com.jewelcart.common.enums.MetalType;
import com.jewelcart.common.enums.OccasionType;
import com.jewelcart.common.exception.DuplicateResourceException;
import com.jewelcart.common.exception.ResourceNotFoundException;
import com.jewelcart.inventory.dto.InitializeStockRequest;
import com.jewelcart.inventory.service.StockService;
import com.jewelcart.product.dto.*;
import com.jewelcart.product.entity.Product;
import com.jewelcart.product.entity.ProductImage;
import com.jewelcart.product.repository.ProductRepository;
import com.jewelcart.vendor.entity.Vendor;
import com.jewelcart.vendor.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;
    private final StockService stockService;

    // ─── WRITE OPERATIONS ────────────────────────────────────────────────────

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        // prevent duplicate SKU at service level before hitting DB constraint
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Product with SKU already exists: " + request.sku());
        }

        // vendor is mandatory — product must belong to a vendor
        Vendor vendor = vendorRepository.findById(request.vendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + request.vendorId()));

        // category is optional — product can exist without category
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));
        }

        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .sku(request.sku())
                .vendor(vendor)
                .category(category)
                .basePrice(request.basePrice())
                .sellingPrice(request.sellingPrice())
                // default GST to 3.00 if not provided (jewellery standard rate)
                .gstRate(request.gstRate() != null ? request.gstRate() : new BigDecimal("3.00"))
                .metalType(request.metalType())
                .weightGrams(request.weightGrams())
                .purity(request.purity())
                .stoneType(request.stoneType())
                .occasion(request.occasion())
                .gender(request.gender())
                .build();

        product = productRepository.save(product);

        stockService.initializeStock(new InitializeStockRequest(
                product.getId(),
                null,           // no variant for base product
                0,              // initial quantity = 0
                5               // default low stock threshold
        ));

        return toResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        // null categoryId → remove category (make uncategorized)
        // non-null categoryId → validate and assign new category
        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        product.setName(request.name());           // NOT NULL — always set
        product.setDescription(request.description()); // nullable — null clears it, fine

        // prices — NOT NULL in DB, only update if provided
        if (request.basePrice() != null) product.setBasePrice(request.basePrice());
        if (request.sellingPrice() != null) product.setSellingPrice(request.sellingPrice());
        if (request.gstRate() != null) product.setGstRate(request.gstRate());
        if (request.isFeatured() != null) product.setIsFeatured(request.isFeatured());

        // nullable fields — null is valid, clears the value intentionally
        product.setMetalType(request.metalType());
        product.setWeightGrams(request.weightGrams());
        product.setPurity(request.purity());
        product.setStoneType(request.stoneType());
        product.setOccasion(request.occasion());
        product.setGender(request.gender());

        // no save() needed — JPA dirty checking detects changes and updates automatically
        return toResponse(product);
    }

    @Transactional
    public void deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        // soft delete — never hard delete products (order history references them)
        product.setIsActive(false);
        // no save() needed — dirty checking handles it
    }

    // ─── READ OPERATIONS ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        // JOIN FETCH vendor — safe on single entity, no pagination issue
        return productRepository.findByIdWithVendor(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getProductsByVendor(Long vendorId, Pageable pageable) {
        // validate vendor exists before querying products
        if (!vendorRepository.existsById(vendorId)) {
            throw new ResourceNotFoundException("Vendor not found with id: " + vendorId);
        }
        return productRepository.findByVendorId(vendorId, pageable)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getProductsByCategory(Long categoryId, Pageable pageable) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }
        return productRepository.findByCategoryId(categoryId, pageable)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> searchProductsByName(String name, Pageable pageable) {
        // LIKE search — case-insensitive, handled in JPQL with LOWER()
        return productRepository.searchByName(name, pageable)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getProductsByMetalTypeAndPriceRange(
            MetalType metalType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {
        return productRepository.findByMetalTypeAndPriceRange(metalType, minPrice, maxPrice, pageable)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getFeaturedProducts(Pageable pageable) {
        return productRepository.findFeaturedProducts(pageable)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getProductsByOccasionAndGender(
            OccasionType occasion,
            GenderType gender,
            Pageable pageable) {
        return productRepository.findByOccasionAndGender(occasion, gender, pageable)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDTO> getRecentProducts(int limit) {
        // guard against abuse — cap at 50
        int safeLimit = Math.min(limit, 50);
        Pageable pageable = PageRequest.of(0, safeLimit,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return productRepository.findRecentActiveProducts(pageable)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VendorProductCount> getVendorProductCounts() {
        // projection-based aggregation — safer than Object[] index access
        return productRepository.countActiveProductsPerVendor();
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    // full detail response — used for single product API
    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getVendor() != null ? product.getVendor().getId() : null,
                product.getVendor() != null ? product.getVendor().getName() : null,
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getBasePrice(),
                product.getSellingPrice(),
                product.getGstRate(),
                product.getMetalType(),
                product.getWeightGrams(),
                product.getPurity(),
                product.getStoneType(),
                product.getOccasion(),
                product.getGender(),
                product.getIsActive(),
                product.getIsFeatured(),
                // extract image URLs only — don't expose entity to API layer
                product.getImages().stream()
                        .map(ProductImage::getImageUrl)
                        .toList(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    // lightweight summary — used for list/search APIs
    private ProductSummaryDTO toSummary(Product product) {
        // find primary image — null if none set
        String primaryImage = product.getImages().stream()
                .filter(ProductImage::getIsPrimary)
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(null);

        return new ProductSummaryDTO(
                product.getId(),
                product.getName(),
                product.getSellingPrice(),
                product.getGstRate(),
                product.getMetalType(),
                product.getOccasion(),
                product.getGender(),
                primaryImage,
                product.getVendor() != null ? product.getVendor().getName() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getIsActive(),
                product.getIsFeatured(),
                product.getCreatedAt()
        );
    }
}
