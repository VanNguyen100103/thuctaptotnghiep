package com.ut.edu.backend.product;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.common.HtmlEntityDecoder;
import com.ut.edu.backend.common.SlugUtil;
import com.ut.edu.backend.exception.SubscriptionRequiredException;
import com.ut.edu.backend.order.OrderRepository;
import com.ut.edu.backend.order.OrderStatus;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Store Product Controller (owner dashboard)
 * Product management for store owners/managers, scoped to their store.
 * List/search queries are tenant-scoped by the Hibernate filter; findById
 * bypasses it, so every by-id access goes through findStoreProduct().
 */
@RestController
@RequestMapping("/store/products")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
public class AdminProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TenantGuard tenantGuard;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductImportService productImportService;

    /** Orders still "in flight" - not yet delivered/cancelled/refunded/failed - whose items count toward "Khách đặt". */
    private static final List<OrderStatus> OPEN_ORDER_STATUSES = List.of(
            OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING, OrderStatus.PENDING_COD,
            OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED);

    /** Stamps each product's transient "Khách đặt" field with its outstanding quantity across the store's open orders. */
    private void decorateWithPendingQuantity(List<Product> products) {
        if (products.isEmpty()) {
            return;
        }
        List<Long> ids = products.stream().map(Product::getId).collect(Collectors.toList());
        Map<Long, Integer> pendingByProduct = orderRepository
                .sumPendingQuantityByProduct(tenantGuard.requireStore(), ids, OPEN_ORDER_STATUSES).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
        products.forEach(p -> p.setPendingCustomerQuantity(pendingByProduct.getOrDefault(p.getId(), 0)));
    }

    /**
     * Load a product only if it belongs to the current store; cross-tenant
     * ids look like "not found" (anti-IDOR).
     */
    private Product findStoreProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(p -> tenantGuard.isCurrentStore(p.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
    }

    /**
     * Get all products with pagination, matching KiotViet's "Hàng hóa" list
     * sidebar filters (Nhóm hàng / Tồn kho).
     * GET /api/store/products?page=0&size=20&active=true&categoryId=1&inStock=true
     */
    @GetMapping
    public ResponseEntity<?> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean inStock) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);

            Specification<Product> spec = Specification.where(null);
            if (active != null) {
                boolean activeValue = active;
                spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), activeValue));
            }
            if (categoryId != null) {
                spec = spec.and((root, query, cb) -> {
                    query.distinct(true);
                    return cb.equal(root.join("categories").get("id"), categoryId);
                });
            }
            if (inStock != null) {
                spec = inStock
                        ? spec.and((root, query, cb) -> cb.greaterThan(root.get("stockQuantity"), 0))
                        : spec.and((root, query, cb) -> cb.equal(root.get("stockQuantity"), 0));
            }

            Page<Product> products = productRepository.findAll(spec, pageable);
            decorateWithPendingQuantity(products.getContent());

            Map<String, Object> response = new HashMap<>();
            response.put("products", products.getContent());
            response.put("currentPage", products.getNumber());
            response.put("totalItems", products.getTotalElements());
            response.put("totalPages", products.getTotalPages());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve products"));
        }
    }

    /**
     * Distinct brand names already used by this store's products, for the
     * "Thương hiệu" autocomplete on the product form - brand is a plain
     * string column (see Product#brand), not a separate entity.
     * GET /api/store/products/brands
     */
    @GetMapping("/brands")
    public ResponseEntity<?> getBrands() {
        return ResponseEntity.ok(Map.of("brands", productRepository.findAllBrands()));
    }

    /**
     * Distinct "Vị trí" values already used by this store's products, for
     * the location autocomplete on the product form (see Product#location).
     * GET /api/store/products/locations
     */
    @GetMapping("/locations")
    public ResponseEntity<?> getLocations() {
        return ResponseEntity.ok(Map.of("locations", productRepository.findAllLocations()));
    }

    /**
     * adminSearchProducts is a native query, so Sort properties go into the SQL
     * as literal column names (unlike getAllProducts' JPQL/Specification query,
     * which resolves them against the entity metamodel) - map the frontend's
     * camelCase field names to real snake_case columns, since e.g. "createdAt"
     * as-is makes Postgres fail on the unknown column "createdat", which
     * surfaced to users as a blanket "Failed to search products".
     */
    private static final Map<String, String> SEARCH_SORT_COLUMNS = Map.of(
            "id", "id",
            "createdAt", "created_at",
            "name", "name",
            "price", "price",
            "stockQuantity", "stock_quantity");

    /**
     * Search products (admin - includes inactive products)
     * GET /api/admin/products/search?query=shirt&page=0&size=20
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Search query is required"));
            }

            // Decode HTML entities if present (e.g., &Aacute; -> Á)
            String decodedQuery = HtmlEntityDecoder.decode(query.trim());
            log.debug("Search query: original='{}', decoded='{}'", query, decodedQuery);

            String sortColumn = SEARCH_SORT_COLUMNS.getOrDefault(sortBy, "created_at");
            Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortColumn).descending()
                : Sort.by(sortColumn).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);

            // Use optimized repository query (searches name, description, brand, SKU)
            // Native query bypasses the Hibernate tenant filter -> pass storeId explicitly
            Page<Product> products = productRepository.adminSearchProducts(
                    decodedQuery, tenantGuard.requireStore(), pageable);
            decorateWithPendingQuantity(products.getContent());

            Map<String, Object> response = new HashMap<>();
            response.put("products", products.getContent());
            response.put("currentPage", products.getNumber());
            response.put("totalItems", products.getTotalElements());
            response.put("totalPages", products.getTotalPages());
            response.put("query", decodedQuery);  // Return decoded query

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to search products with query: {}", query, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to search products"));
        }
    }

    /**
     * Get product by ID
     * GET /api/admin/products/{productId}
     */
    @GetMapping("/{productId}")
    public ResponseEntity<?> getProductById(@PathVariable Long productId) {
        try {
            Product product = findStoreProduct(productId);

            return ResponseEntity.ok(product);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get product: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve product"));
        }
    }

    /**
     * Sibling products sharing this product's variantGroupId - powers the POS
     * cart line's "Đơn vị tính" dropdown, letting a cashier reprice a line to
     * a sibling unit variant (e.g. Cái -> Thùng) without re-searching the
     * grid. Units aren't a distinct backend concept (see UnitDef on the
     * frontend) - each unit is just another generated row in the same
     * variant group, so this reuses the existing variant-group lookup.
     * GET /api/store/products/{productId}/unit-siblings
     */
    @GetMapping("/{productId}/unit-siblings")
    public ResponseEntity<?> getUnitSiblings(@PathVariable Long productId) {
        try {
            Product product = findStoreProduct(productId);
            List<Product> siblings = product.getVariantGroupId() == null
                    ? List.of(product)
                    : productRepository.findByVariantGroupIdOrderByIdAsc(product.getVariantGroupId());
            return ResponseEntity.ok(Map.of("products", siblings));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get unit siblings: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve product unit variants"));
        }
    }

    /**
     * Create new product
     * POST /api/admin/products
     */
    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody Product product) {
        try {
            Long storeId = tenantGuard.requireStore();
            subscriptionGuard.requireCanAddProduct(storeId, productRepository.countByStoreId(storeId));

            // Validate required fields
            if (product.getName() == null || product.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product name is required"));
            }

            if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Valid price is required"));
            }

            // Set default values
            if (product.getActive() == null) {
                product.setActive(true);
            }
            if (product.getStockQuantity() == null) {
                product.setStockQuantity(0);
            }

            // taxRate is OWNER-only - drop it if a MANAGER (or anyone else this
            // endpoint allows) tried to set it, rather than silently trusting the body
            if (!authorizationService.hasRole("OWNER")) {
                product.setTaxRate(null);
            }

            // New products always belong to the current store
            product.setStore(tenantGuard.currentStoreRef());

            Product savedProduct = productRepository.save(product);

            log.info("New product created: {} (ID: {})", savedProduct.getName(), savedProduct.getId());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Product created successfully",
                            "product", savedProduct
                    ));

        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to create product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create product"));
        }
    }

    /**
     * Create a batch of variants of "the same" product - each combination of
     * free-named attribute values (not hardcoded to color/size - a store can
     * define whatever axes fit its industry, e.g. "Hương vị" for F&B) becomes
     * its own independently trackable Product row (own sku/price/stock), all
     * sharing one generated variantGroupId. All-or-nothing: nothing is
     * persisted if the batch would exceed the store's plan limit, contains a
     * duplicate/colliding SKU, or a row's attribute keys don't match the
     * declared attributeOrder.
     * POST /api/store/products/variants
     */
    @PostMapping("/variants")
    public ResponseEntity<?> createProductVariants(@Valid @RequestBody CreateProductVariantsRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            List<CreateProductVariantsRequest.VariantRow> rows = request.getVariants();
            List<String> attributeOrder = request.getAttributeOrder();
            Set<String> expectedNames = new HashSet<>(attributeOrder);

            Set<String> comboSeen = new HashSet<>();
            Set<String> skuSeen = new HashSet<>();
            for (var row : rows) {
                if (!row.getAttributeValues().keySet().equals(expectedNames)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Every variant row must have exactly the attributes declared in attributeOrder: "
                                    + attributeOrder));
                }
                String comboKey = attributeOrder.stream()
                        .map(name -> row.getAttributeValues().get(name).trim().toLowerCase())
                        .collect(Collectors.joining("|"));
                if (!comboSeen.add(comboKey)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Duplicate attribute combination in request: " + row.getAttributeValues()));
                }
                if (!skuSeen.add(row.getSku().trim().toLowerCase())) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Duplicate SKU in request: " + row.getSku()));
                }
            }

            subscriptionGuard.requireCanAddProducts(storeId, productRepository.countByStoreId(storeId), rows.size());

            Set<Category> categories = resolveCategories(request.getCategoryIds());

            for (var row : rows) {
                if (Boolean.TRUE.equals(productRepository.existsBySku(row.getSku().trim()))) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "SKU already exists: " + row.getSku()));
                }
            }

            boolean ownerCall = authorizationService.hasRole("OWNER");
            Store storeRef = tenantGuard.currentStoreRef();
            String variantGroupId = UUID.randomUUID().toString();
            Set<String> usedSlugs = new HashSet<>();

            List<Product> toSave = rows.stream().map(row -> {
                String attributeSuffix = attributeOrder.stream()
                        .map(name -> row.getAttributeValues().get(name).trim())
                        .collect(Collectors.joining(" - "));
                Product p = new Product();
                p.setStore(storeRef);
                p.setName("%s - %s".formatted(request.getName(), attributeSuffix));
                p.setSlug(uniqueSlug(SlugUtil.slugify(p.getName()), usedSlugs));
                p.setSku(row.getSku().trim());
                p.setBarcode(row.getBarcode());
                p.setShortDescription(request.getShortDescription());
                p.setDescription(request.getDescription());
                p.setPrice(row.getPrice());
                p.setCompareAtPrice(request.getCompareAtPrice());
                p.setCostPrice(row.getCostPrice());
                p.setStockQuantity(row.getStockQuantity());
                p.setMinStockThreshold(request.getMinStockThreshold());
                p.setMaxStockThreshold(request.getMaxStockThreshold());
                p.setTaxRate(ownerCall ? request.getTaxRate() : null);
                p.setActive(row.getActive() != null ? row.getActive() : (request.getActive() != null ? request.getActive() : true));
                p.setFeatured(request.getFeatured() != null ? request.getFeatured() : false);
                p.setAttributes(new HashMap<>(row.getAttributeValues()));
                p.setBrand(request.getBrand());
                p.setMaterial(request.getMaterial());
                p.setGender(request.getGender());
                p.setLocation(request.getLocation());
                p.setWeight(request.getWeight());
                p.setWeightUnit(request.getWeightUnit());
                p.setWidth(request.getWidth());
                p.setLength(request.getLength());
                p.setHeight(request.getHeight());
                p.setDimensionUnit(request.getDimensionUnit());
                p.setLoyaltyPointsEnabled(request.getLoyaltyPointsEnabled() != null ? request.getLoyaltyPointsEnabled() : true);
                p.setCategories(new HashSet<>(categories));
                p.setVariantGroupId(variantGroupId);
                return p;
            }).collect(Collectors.toList());

            List<Product> saved = productRepository.saveAll(toSave);

            log.info("Created {} product variants for store {}: variantGroupId={}",
                    saved.size(), storeId, variantGroupId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Created " + saved.size() + " product variants",
                    "variantGroupId", variantGroupId,
                    "products", saved
            ));

        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to create product variants", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create product variants"));
        }
    }

    /**
     * Sample .xlsx for bulk import - matches ProductImportService's fixed column layout.
     * GET /api/store/products/import/template
     */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        byte[] file = productImportService.generateTemplate();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"mau-nhap-hang-hoa.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    /**
     * Bulk import products from an .xlsx file, matching KiotViet's "Nhập hàng
     * hóa từ file dữ liệu" dialog options.
     * POST /api/store/products/import (multipart/form-data)
     */
    @PostMapping("/import")
    public ResponseEntity<?> importProducts(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean replaceDuplicateName,
            @RequestParam(defaultValue = "false") boolean replaceDuplicateSku,
            @RequestParam(defaultValue = "false") boolean updateStock,
            @RequestParam(defaultValue = "false") boolean updateCostPrice,
            @RequestParam(defaultValue = "false") boolean updateDescription) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File dữ liệu không được để trống"));
        }
        try {
            ProductImportOptions options = new ProductImportOptions(
                    replaceDuplicateName, replaceDuplicateSku, updateStock, updateCostPrice, updateDescription);
            ProductImportResult result = productImportService.importFromExcel(file, options);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to import products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to import products"));
        }
    }

    /** Looks up categories by id, throwing if any id doesn't exist - same validation shape as updateProductCategories. */
    private Set<Category> resolveCategories(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Set.of();
        }
        List<Category> found = categoryRepository.findAllById(categoryIds);
        if (found.size() != categoryIds.size()) {
            Set<Long> foundIds = found.stream().map(Category::getId).collect(Collectors.toSet());
            Set<Long> missingIds = new HashSet<>(categoryIds);
            missingIds.removeAll(foundIds);
            throw new IllegalArgumentException("Some categories not found: " + missingIds);
        }
        return new HashSet<>(found);
    }

    /** Appends -2, -3, ... on collision against both the DB and other rows in this same batch. */
    private String uniqueSlug(String baseSlug, Set<String> usedInBatch) {
        String candidate = baseSlug;
        int suffix = 2;
        while (usedInBatch.contains(candidate) || Boolean.TRUE.equals(productRepository.existsBySlug(candidate))) {
            candidate = baseSlug + "-" + suffix++;
        }
        usedInBatch.add(candidate);
        return candidate;
    }

    /**
     * Update product
     * PUT /api/admin/products/{productId}
     */
    @PutMapping("/{productId}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long productId,
            @RequestBody Product productUpdates) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            Product existingProduct = findStoreProduct(productId);

            // Update fields
            if (productUpdates.getName() != null) {
                existingProduct.setName(productUpdates.getName());
            }
            if (productUpdates.getDescription() != null) {
                existingProduct.setDescription(productUpdates.getDescription());
            }
            if (productUpdates.getNotes() != null) {
                existingProduct.setNotes(productUpdates.getNotes());
            }
            if (productUpdates.getBarcode() != null) {
                existingProduct.setBarcode(productUpdates.getBarcode());
            }
            if (productUpdates.getPrice() != null) {
                existingProduct.setPrice(productUpdates.getPrice());
            }
            if (productUpdates.getStockQuantity() != null) {
                existingProduct.setStockQuantity(productUpdates.getStockQuantity());
            }
            if (productUpdates.getBrand() != null) {
                existingProduct.setBrand(productUpdates.getBrand());
            }
            if (productUpdates.getMaterial() != null) {
                existingProduct.setMaterial(productUpdates.getMaterial());
            }
            if (productUpdates.getGender() != null) {
                existingProduct.setGender(productUpdates.getGender());
            }
            if (productUpdates.getCompareAtPrice() != null) {
                existingProduct.setCompareAtPrice(productUpdates.getCompareAtPrice());
            }
            if (productUpdates.getCostPrice() != null) {
                existingProduct.setCostPrice(productUpdates.getCostPrice());
            }
            if (productUpdates.getMinStockThreshold() != null) {
                existingProduct.setMinStockThreshold(productUpdates.getMinStockThreshold());
            }
            if (productUpdates.getMaxStockThreshold() != null) {
                existingProduct.setMaxStockThreshold(productUpdates.getMaxStockThreshold());
            }
            if (productUpdates.getLocation() != null) {
                existingProduct.setLocation(productUpdates.getLocation());
            }
            if (productUpdates.getWeight() != null) {
                existingProduct.setWeight(productUpdates.getWeight());
            }
            if (productUpdates.getWeightUnit() != null) {
                existingProduct.setWeightUnit(productUpdates.getWeightUnit());
            }
            if (productUpdates.getWidth() != null) {
                existingProduct.setWidth(productUpdates.getWidth());
            }
            if (productUpdates.getLength() != null) {
                existingProduct.setLength(productUpdates.getLength());
            }
            if (productUpdates.getHeight() != null) {
                existingProduct.setHeight(productUpdates.getHeight());
            }
            if (productUpdates.getDimensionUnit() != null) {
                existingProduct.setDimensionUnit(productUpdates.getDimensionUnit());
            }
            if (productUpdates.getLoyaltyPointsEnabled() != null) {
                existingProduct.setLoyaltyPointsEnabled(productUpdates.getLoyaltyPointsEnabled());
            }
            // taxRate is OWNER-only - a MANAGER's edit request simply can't touch it,
            // even if the field is present in the body
            if (productUpdates.getTaxRate() != null && authorizationService.hasRole("OWNER")) {
                existingProduct.setTaxRate(productUpdates.getTaxRate());
            }
            // Update available sizes and colors
            if (productUpdates.getAvailableSizes() != null) {
                existingProduct.setAvailableSizes(productUpdates.getAvailableSizes());
            }
            if (productUpdates.getAvailableColors() != null) {
                existingProduct.setAvailableColors(productUpdates.getAvailableColors());
            }
            if (productUpdates.getCategories() != null) {
                existingProduct.setCategories(productUpdates.getCategories());
            }

            Product savedProduct = productRepository.save(existingProduct);

            log.info("Product updated: {} (ID: {})", savedProduct.getName(), savedProduct.getId());

            return ResponseEntity.ok(Map.of(
                    "message", "Product updated successfully",
                    "product", savedProduct
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update product: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update product"));
        }
    }

    /**
     * Update product stock
     * PATCH /api/admin/products/{productId}/stock
     */
    @PatchMapping("/{productId}/stock")
    public ResponseEntity<?> updateProductStock(
            @PathVariable Long productId,
            @RequestBody Map<String, Integer> request) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            Product product = findStoreProduct(productId);

            Integer stockQuantity = request.get("stockQuantity");
            if (stockQuantity == null || stockQuantity < 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Valid stock quantity is required"));
            }

            product.setStockQuantity(stockQuantity);
            productRepository.save(product);

            log.info("Product {} stock updated to: {}", productId, stockQuantity);

            return ResponseEntity.ok(Map.of(
                    "message", "Stock updated successfully",
                    "productId", productId,
                    "stockQuantity", stockQuantity
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update product stock: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update stock"));
        }
    }

    /**
     * Activate/Deactivate product
     * PATCH /api/admin/products/{productId}/status
     */
    @PatchMapping("/{productId}/status")
    public ResponseEntity<?> updateProductStatus(
            @PathVariable Long productId,
            @RequestBody Map<String, Boolean> request) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            Product product = findStoreProduct(productId);

            Boolean active = request.get("active");
            if (active == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "active field is required"));
            }

            product.setActive(active);
            productRepository.save(product);

            String status = active ? "activated" : "deactivated";
            log.info("Product {} {}", productId, status);

            return ResponseEntity.ok(Map.of(
                    "message", "Product " + status + " successfully",
                    "productId", productId,
                    "active", active
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update product status: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update product status"));
        }
    }

    /**
     * Delete product (soft delete - set active to false)
     * DELETE /api/admin/products/{productId}
     */
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> deleteProduct(@PathVariable Long productId) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            Product product = findStoreProduct(productId);

            // Soft delete
            product.setActive(false);
            productRepository.save(product);

            log.warn("Product {} deleted (deactivated) by admin", productId);

            return ResponseEntity.ok(Map.of(
                    "message", "Product deleted successfully",
                    "productId", productId
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to delete product: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete product"));
        }
    }

    /**
     * Get product statistics
     * GET /api/admin/products/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getProductStatistics() {
        try {
            long totalProducts = productRepository.count();

            // Count active and inactive products
            long activeProducts = productRepository.findAll().stream()
                .filter(Product::getActive)
                .count();
            long inactiveProducts = totalProducts - activeProducts;

            // Count out of stock products
            long outOfStock = productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() == 0)
                .count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalProducts", totalProducts);
            stats.put("activeProducts", activeProducts);
            stats.put("inactiveProducts", inactiveProducts);
            stats.put("outOfStock", outOfStock);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to get product statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve statistics"));
        }
    }

    /**
     * Bulk update product prices (e.g., apply discount)
     * POST /api/admin/products/bulk-price-update
     */
    @PostMapping("/bulk-price-update")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> bulkPriceUpdate(@RequestBody Map<String, Object> request) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());

            // Validate and parse input
            @SuppressWarnings("unchecked")
            List<?> rawProductIds = (List<?>) request.get("productIds");
            
            // Convert to List<Long> (handles both Integer and Long from JSON)
            List<Long> productIds = new ArrayList<>();
            if (rawProductIds != null) {
                for (Object id : rawProductIds) {
                    if (id instanceof Integer) {
                        productIds.add(((Integer) id).longValue());
                    } else if (id instanceof Long) {
                        productIds.add((Long) id);
                    } else if (id != null) {
                        productIds.add(Long.parseLong(id.toString()));
                    }
                }
            }
            
            String action = (String) request.get("action");
            
            // Safe number conversion
            Object percentageObj = request.get("percentage");
            BigDecimal percentage;
            if (percentageObj instanceof Integer) {
                percentage = new BigDecimal((Integer) percentageObj);
            } else if (percentageObj instanceof Double) {
                percentage = BigDecimal.valueOf((Double) percentageObj);
            } else if (percentageObj != null) {
                percentage = new BigDecimal(percentageObj.toString());
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "percentage is required"));
            }

            // Validation
            if (productIds == null || productIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product IDs are required"));
            }
            
            if (!"increase".equals(action) && !"decrease".equals(action)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "action must be 'increase' or 'decrease'"));
            }
            
            if (percentage.compareTo(BigDecimal.ZERO) <= 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "percentage must be between 0 and 100"));
            }

            int updatedCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (Long productId : productIds) {
                try {
                    Product product = productRepository.findById(productId)
                            .filter(p -> tenantGuard.isCurrentStore(p.getStore()))
                            .orElse(null);

                    if (product == null) {
                        errors.add("Product " + productId + " not found");
                        continue;
                    }
                    
                    BigDecimal currentPrice = product.getPrice();
                    if (currentPrice == null) {
                        errors.add("Product " + productId + " has null price");
                        continue;
                    }
                    
                    // FIX: Correct BigDecimal division with proper scale
                    BigDecimal multiplier = percentage.divide(
                        new BigDecimal("100"), 
                        4,  // 4 decimal places for precision
                        RoundingMode.HALF_UP
                    );
                    
                    BigDecimal change = currentPrice.multiply(multiplier);

                    BigDecimal newPrice;
                    if ("increase".equals(action)) {
                        newPrice = currentPrice.add(change);
                    } else {
                        newPrice = currentPrice.subtract(change);
                    }
                    
                    // Round to 2 decimal places for currency
                    newPrice = newPrice.setScale(2, RoundingMode.HALF_UP);
                    
                    // Prevent negative price
                    if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Product " + productId + " would have negative price");
                        continue;
                    }

                    product.setPrice(newPrice);
                    productRepository.save(product);
                    updatedCount++;
                    
                    log.info("Product {} price updated: {} -> {} ({}% {})", 
                        productId, currentPrice, newPrice, percentage, action);
                    
                } catch (Exception e) {
                    errors.add("Product " + productId + ": " + e.getMessage());
                    log.error("Error updating product {}", productId, e);
                }
            }

            log.info("Bulk price update completed: {} products updated, {} errors", 
                updatedCount, errors.size());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Bulk update completed");
            response.put("updatedCount", updatedCount);
            response.put("totalRequested", productIds.size());
            
            if (!errors.isEmpty()) {
                response.put("errors", errors);
            }

            return ResponseEntity.ok(response);

        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to bulk update prices", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to update prices",
                        "details", e.getMessage()
                    ));
        }
    }

    /**
     * Update product categories
     * PATCH /api/admin/products/{productId}/categories
     *
     * Request body:
     * {
     *   "categoryIds": [1, 2, 3]
     * }
     */
    @PatchMapping("/{productId}/categories")
    public ResponseEntity<?> updateProductCategories(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());

            // Find product
            Product product = findStoreProduct(productId);

            // Extract category IDs from request
            @SuppressWarnings("unchecked")
            List<?> rawCategoryIds = (List<?>) request.get("categoryIds");

            if (rawCategoryIds == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "categoryIds field is required"));
            }

            // Convert to List<Long>
            Set<Long> categoryIds = new HashSet<>();
            for (Object id : rawCategoryIds) {
                if (id instanceof Integer) {
                    categoryIds.add(((Integer) id).longValue());
                } else if (id instanceof Long) {
                    categoryIds.add((Long) id);
                } else if (id != null) {
                    categoryIds.add(Long.parseLong(id.toString()));
                }
            }

            // Fetch categories from database
            List<Category> categories = categoryRepository.findAllById(categoryIds);

            // Check if all categories exist
            if (categories.size() != categoryIds.size()) {
                Set<Long> foundIds = categories.stream()
                        .map(Category::getId)
                        .collect(Collectors.toSet());

                Set<Long> missingIds = new HashSet<>(categoryIds);
                missingIds.removeAll(foundIds);

                return ResponseEntity.badRequest()
                        .body(Map.of(
                            "error", "Some categories not found",
                            "missingIds", missingIds
                        ));
            }

            // Update product categories
            product.setCategories(new HashSet<>(categories));
            Product savedProduct = productRepository.save(product);

            log.info("Product {} categories updated: {} categories assigned",
                    productId, categories.size());

            return ResponseEntity.ok(Map.of(
                    "message", "Product categories updated successfully",
                    "productId", productId,
                    "categories", savedProduct.getCategories().stream()
                            .map(c -> Map.of(
                                "id", c.getId(),
                                "name", c.getName(),
                                "slug", c.getSlug()
                            ))
                            .collect(Collectors.toList())
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update product categories: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to update product categories",
                        "details", e.getMessage()
                    ));
        }
    }

    /**
     * Add categories to product (append, don't replace)
     * POST /api/admin/products/{productId}/categories
     *
     * Request body:
     * {
     *   "categoryIds": [1, 2]
     * }
     */
    @PostMapping("/{productId}/categories")
    public ResponseEntity<?> addProductCategories(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());

            // Find product
            Product product = findStoreProduct(productId);

            // Extract category IDs from request
            @SuppressWarnings("unchecked")
            List<?> rawCategoryIds = (List<?>) request.get("categoryIds");

            if (rawCategoryIds == null || rawCategoryIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "categoryIds field is required and must not be empty"));
            }

            // Convert to List<Long>
            Set<Long> categoryIds = new HashSet<>();
            for (Object id : rawCategoryIds) {
                if (id instanceof Integer) {
                    categoryIds.add(((Integer) id).longValue());
                } else if (id instanceof Long) {
                    categoryIds.add((Long) id);
                } else if (id != null) {
                    categoryIds.add(Long.parseLong(id.toString()));
                }
            }

            // Fetch categories from database
            List<Category> newCategories = categoryRepository.findAllById(categoryIds);

            // Check if all categories exist
            if (newCategories.size() != categoryIds.size()) {
                Set<Long> foundIds = newCategories.stream()
                        .map(Category::getId)
                        .collect(Collectors.toSet());

                Set<Long> missingIds = new HashSet<>(categoryIds);
                missingIds.removeAll(foundIds);

                return ResponseEntity.badRequest()
                        .body(Map.of(
                            "error", "Some categories not found",
                            "missingIds", missingIds
                        ));
            }

            // Add new categories to existing ones
            Set<Category> existingCategories = product.getCategories();
            if (existingCategories == null) {
                existingCategories = new HashSet<>();
            }

            int beforeCount = existingCategories.size();
            existingCategories.addAll(newCategories);
            int addedCount = existingCategories.size() - beforeCount;

            product.setCategories(existingCategories);
            Product savedProduct = productRepository.save(product);

            log.info("Product {} categories added: {} new categories (total: {})",
                    productId, addedCount, existingCategories.size());

            return ResponseEntity.ok(Map.of(
                    "message", "Categories added successfully",
                    "productId", productId,
                    "addedCount", addedCount,
                    "totalCategories", existingCategories.size(),
                    "categories", savedProduct.getCategories().stream()
                            .map(c -> Map.of(
                                "id", c.getId(),
                                "name", c.getName(),
                                "slug", c.getSlug()
                            ))
                            .collect(Collectors.toList())
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to add product categories: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to add product categories",
                        "details", e.getMessage()
                    ));
        }
    }

    /**
     * Remove categories from product
     * DELETE /api/admin/products/{productId}/categories
     *
     * Request body:
     * {
     *   "categoryIds": [1, 2]
     * }
     */
    @DeleteMapping("/{productId}/categories")
    public ResponseEntity<?> removeProductCategories(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());

            // Find product
            Product product = findStoreProduct(productId);

            // Extract category IDs from request
            @SuppressWarnings("unchecked")
            List<?> rawCategoryIds = (List<?>) request.get("categoryIds");

            if (rawCategoryIds == null || rawCategoryIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "categoryIds field is required and must not be empty"));
            }

            // Convert to List<Long>
            Set<Long> categoryIdsToRemove = new HashSet<>();
            for (Object id : rawCategoryIds) {
                if (id instanceof Integer) {
                    categoryIdsToRemove.add(((Integer) id).longValue());
                } else if (id instanceof Long) {
                    categoryIdsToRemove.add((Long) id);
                } else if (id != null) {
                    categoryIdsToRemove.add(Long.parseLong(id.toString()));
                }
            }

            // Remove categories
            Set<Category> existingCategories = product.getCategories();
            if (existingCategories == null || existingCategories.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", "Product has no categories to remove",
                        "productId", productId
                ));
            }

            int beforeCount = existingCategories.size();
            existingCategories.removeIf(c -> categoryIdsToRemove.contains(c.getId()));
            int removedCount = beforeCount - existingCategories.size();

            product.setCategories(existingCategories);
            Product savedProduct = productRepository.save(product);

            log.info("Product {} categories removed: {} categories deleted (remaining: {})",
                    productId, removedCount, existingCategories.size());

            return ResponseEntity.ok(Map.of(
                    "message", "Categories removed successfully",
                    "productId", productId,
                    "removedCount", removedCount,
                    "remainingCategories", existingCategories.size(),
                    "categories", savedProduct.getCategories().stream()
                            .map(c -> Map.of(
                                "id", c.getId(),
                                "name", c.getName(),
                                "slug", c.getSlug()
                            ))
                            .collect(Collectors.toList())
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to remove product categories: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to remove product categories",
                        "details", e.getMessage()
                    ));
        }
    }
}
