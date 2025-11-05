package com.ut.edu.backend.controller;

import com.ut.edu.backend.model.Category;
import com.ut.edu.backend.repository.CategoryRepository;
import com.ut.edu.backend.service.inter.ICloudinaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Category Controller
 * Manages product categories with hierarchical support
 */
@RestController
@RequestMapping("/categories")
@Slf4j
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ICloudinaryService cloudinaryService;

    /**
     * Get all active categories
     * GET /api/categories
     */
    @GetMapping
    public ResponseEntity<?> getAllCategories() {
        try {
            List<Category> categories = categoryRepository.findByActiveTrueOrderByDisplayOrderAsc();

            List<Map<String, Object>> categoryList = categories.stream()
                    .map(this::categoryToMap)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "categories", categoryList,
                    "total", categories.size()
            ));

        } catch (Exception e) {
            log.error("Failed to get categories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve categories"));
        }
    }

    /**
     * Get root categories (categories without parent)
     * GET /api/categories/root
     */
    @GetMapping("/root")
    public ResponseEntity<?> getRootCategories() {
        try {
            List<Category> categories = categoryRepository.findRootCategories();

            List<Map<String, Object>> categoryList = categories.stream()
                    .map(this::categoryToMap)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "categories", categoryList,
                    "total", categories.size()
            ));

        } catch (Exception e) {
            log.error("Failed to get root categories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve root categories"));
        }
    }

    /**
     * Get category by ID with children
     * GET /api/categories/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCategoryById(@PathVariable Long id) {
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

            Map<String, Object> categoryData = categoryToMapWithChildren(category);

            return ResponseEntity.ok(categoryData);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get category: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve category"));
        }
    }

    /**
     * Get category by slug
     * GET /api/categories/slug/{slug}
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<?> getCategoryBySlug(@PathVariable String slug) {
        try {
            Category category = categoryRepository.findBySlug(slug)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found with slug: " + slug));

            Map<String, Object> categoryData = categoryToMapWithChildren(category);

            return ResponseEntity.ok(categoryData);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get category by slug: {}", slug, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve category"));
        }
    }

    /**
     * Get subcategories of a parent category
     * GET /api/categories/{parentId}/children
     */
    @GetMapping("/{parentId}/children")
    public ResponseEntity<?> getSubcategories(@PathVariable Long parentId) {
        try {
            List<Category> subcategories = categoryRepository.findByParentId(parentId);

            List<Map<String, Object>> categoryList = subcategories.stream()
                    .map(this::categoryToMap)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "categories", categoryList,
                    "parentId", parentId,
                    "total", subcategories.size()
            ));

        } catch (Exception e) {
            log.error("Failed to get subcategories for parent: {}", parentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve subcategories"));
        }
    }

    /**
     * Create new category with optional image upload
     * POST /api/categories
     *
     * Form-data:
     * - name: string (required)
     * - slug: string (required)
     * - description: string (optional)
     * - displayOrder: integer (optional, default: 0)
     * - parentId: long (optional)
     * - image: file (optional) - ảnh sẽ lưu vào thư mục categories/{slug} trên Cloudinary
     */
    @PostMapping(consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createCategory(
            @RequestParam("name") String name,
            @RequestParam("slug") String slug,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "displayOrder", required = false, defaultValue = "0") Integer displayOrder,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        try {
            // Validate required fields
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Category name is required"));
            }

            if (slug == null || slug.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Category slug is required"));
            }

            // Check if name or slug already exists
            if (categoryRepository.existsByName(name)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Category with this name already exists"));
            }

            if (categoryRepository.existsBySlug(slug)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Category with this slug already exists"));
            }

            // Validate image if provided
            if (image != null && !image.isEmpty()) {
                if (!cloudinaryService.isValidImage(image)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid image file. Allowed: JPG, PNG, GIF, WEBP (max 10MB)"));
                }
            }

            // Create category
            Category category = new Category();
            category.setName(name);
            category.setSlug(slug);
            category.setDescription(description);
            category.setDisplayOrder(displayOrder);
            category.setActive(true);

            // Set parent if provided
            if (parentId != null) {
                Category parent = categoryRepository.findById(parentId)
                        .orElseThrow(() -> new IllegalArgumentException("Parent category not found: " + parentId));
                category.setParent(parent);
            }

            // Save category first to get ID
            Category savedCategory = categoryRepository.save(category);

            // Upload image if provided
            if (image != null && !image.isEmpty()) {
                try {
                    String imageUrl = cloudinaryService.uploadCategoryImage(image, slug);
                    savedCategory.setImageUrl(imageUrl);
                    savedCategory = categoryRepository.save(savedCategory);
                    log.info("Category {} image uploaded to Cloudinary: {}", savedCategory.getId(), imageUrl);
                } catch (IOException e) {
                    log.error("Failed to upload category image, but category was created", e);
                    // Category is already created, just warn about image upload failure
                }
            }

            log.info("New category created: {} (ID: {})", savedCategory.getName(), savedCategory.getId());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Category created successfully" +
                                      (savedCategory.getImageUrl() != null ? " with image" : ""),
                            "category", categoryToMap(savedCategory)
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to create category", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to create category",
                            "details", e.getMessage()
                    ));
        }
    }

    /**
     * Update category
     * PUT /api/categories/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateCategory(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

            // Update name if provided
            String name = (String) request.get("name");
            if (name != null && !name.trim().isEmpty()) {
                // Check if another category has this name
                if (!category.getName().equals(name) && categoryRepository.existsByName(name)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Category with this name already exists"));
                }
                category.setName(name);
            }

            // Update slug if provided
            String slug = (String) request.get("slug");
            if (slug != null && !slug.trim().isEmpty()) {
                // Check if another category has this slug
                if (!category.getSlug().equals(slug) && categoryRepository.existsBySlug(slug)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Category with this slug already exists"));
                }
                category.setSlug(slug);
            }

            // Update other fields
            if (request.containsKey("description")) {
                category.setDescription((String) request.get("description"));
            }

            if (request.containsKey("imageUrl")) {
                category.setImageUrl((String) request.get("imageUrl"));
            }

            if (request.containsKey("active")) {
                category.setActive((Boolean) request.get("active"));
            }

            if (request.containsKey("displayOrder")) {
                Object displayOrderObj = request.get("displayOrder");
                if (displayOrderObj instanceof Integer) {
                    category.setDisplayOrder((Integer) displayOrderObj);
                } else if (displayOrderObj != null) {
                    category.setDisplayOrder(Integer.parseInt(displayOrderObj.toString()));
                }
            }

            // Update parent if provided
            if (request.containsKey("parentId")) {
                Object parentIdObj = request.get("parentId");
                if (parentIdObj == null) {
                    category.setParent(null);
                } else {
                    Long parentId;
                    if (parentIdObj instanceof Integer) {
                        parentId = ((Integer) parentIdObj).longValue();
                    } else {
                        parentId = Long.parseLong(parentIdObj.toString());
                    }

                    // Prevent setting itself as parent
                    if (parentId.equals(id)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Category cannot be its own parent"));
                    }

                    Category parent = categoryRepository.findById(parentId)
                            .orElseThrow(() -> new IllegalArgumentException("Parent category not found: " + parentId));

                    category.setParent(parent);
                }
            }

            Category savedCategory = categoryRepository.save(category);

            log.info("Category updated: {} (ID: {})", savedCategory.getName(), savedCategory.getId());

            return ResponseEntity.ok(Map.of(
                    "message", "Category updated successfully",
                    "category", categoryToMap(savedCategory)
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update category: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to update category",
                            "details", e.getMessage()
                    ));
        }
    }

    /**
     * Delete category (soft delete)
     * DELETE /api/categories/{id}
     * Xóa category (soft delete) VÀ xóa ảnh trên Cloudinary
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

            // Delete image from Cloudinary if exists
            boolean imageDeleted = false;
            if (category.getImageUrl() != null && !category.getImageUrl().isEmpty()) {
                String imageUrl = category.getImageUrl();
                imageDeleted = cloudinaryService.deleteImageByUrl(imageUrl);

                if (imageDeleted) {
                    log.info("Category {} image deleted from Cloudinary: {}", id, imageUrl);
                } else {
                    log.warn("Failed to delete category {} image from Cloudinary: {}", id, imageUrl);
                }
            }

            // Soft delete category
            category.setActive(false);
            categoryRepository.save(category);

            log.warn("Category {} deleted (deactivated) by admin", id);

            return ResponseEntity.ok(Map.of(
                    "message", "Category deleted successfully" +
                               (imageDeleted ? " with image removed from Cloudinary" : ""),
                    "categoryId", id,
                    "imageDeleted", imageDeleted
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to delete category: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete category"));
        }
    }

    /**
     * Get category tree (hierarchical structure)
     * GET /api/categories/tree
     */
    @GetMapping("/tree")
    public ResponseEntity<?> getCategoryTree() {
        try {
            List<Category> rootCategories = categoryRepository.findRootCategories();

            List<Map<String, Object>> tree = rootCategories.stream()
                    .map(this::buildCategoryTree)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "categories", tree,
                    "total", rootCategories.size()
            ));

        } catch (Exception e) {
            log.error("Failed to get category tree", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve category tree"));
        }
    }

    // Helper methods

    private Map<String, Object> categoryToMap(Category category) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", category.getId());
        map.put("name", category.getName());
        map.put("slug", category.getSlug());
        map.put("description", category.getDescription());
        map.put("imageUrl", category.getImageUrl());
        map.put("active", category.getActive());
        map.put("displayOrder", category.getDisplayOrder());
        map.put("parentId", category.getParent() != null ? category.getParent().getId() : null);
        map.put("childrenCount", category.getChildren() != null ? category.getChildren().size() : 0);
        return map;
    }

    private Map<String, Object> categoryToMapWithChildren(Category category) {
        Map<String, Object> map = categoryToMap(category);

        if (category.getChildren() != null && !category.getChildren().isEmpty()) {
            List<Map<String, Object>> children = category.getChildren().stream()
                    .filter(Category::getActive)
                    .sorted(Comparator.comparing(Category::getDisplayOrder))
                    .map(this::categoryToMap)
                    .collect(Collectors.toList());
            map.put("children", children);
        } else {
            map.put("children", Collections.emptyList());
        }

        return map;
    }

    private Map<String, Object> buildCategoryTree(Category category) {
        Map<String, Object> map = categoryToMap(category);

        if (category.getChildren() != null && !category.getChildren().isEmpty()) {
            List<Map<String, Object>> children = category.getChildren().stream()
                    .filter(Category::getActive)
                    .sorted(Comparator.comparing(Category::getDisplayOrder))
                    .map(this::buildCategoryTree)  // Recursive call
                    .collect(Collectors.toList());
            map.put("children", children);
        } else {
            map.put("children", Collections.emptyList());
        }

        return map;
    }

    /**
     * Upload image for category
     * POST /api/categories/{id}/image
     *
     * Form-data:
     * - image: file
     */
    @PostMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadCategoryImage(
            @PathVariable Long id,
            @RequestParam("image") MultipartFile image) {
        try {
            // Find category
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

            // Validate image
            if (image == null || image.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Image file is required"));
            }

            if (!cloudinaryService.isValidImage(image)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid image file. Allowed: JPG, PNG, GIF, WEBP (max 10MB)"));
            }

            // Upload to Cloudinary in categories/{slug}/ folder
            String imageUrl = cloudinaryService.uploadCategoryImage(image, category.getSlug());

            // Update category with new image URL
            category.setImageUrl(imageUrl);
            Category savedCategory = categoryRepository.save(category);

            log.info("Category {} image uploaded successfully: {}", id, imageUrl);

            return ResponseEntity.ok(Map.of(
                    "message", "Category image uploaded successfully",
                    "category", categoryToMap(savedCategory),
                    "imageUrl", imageUrl
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (IOException e) {
            log.error("Failed to upload category image: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to upload image",
                            "details", e.getMessage()
                    ));

        } catch (Exception e) {
            log.error("Unexpected error uploading category image: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload category image"));
        }
    }

    /**
     * Delete category image
     * DELETE /api/categories/{id}/image
     * Xóa cả trong database VÀ trên Cloudinary
     */
    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteCategoryImage(@PathVariable Long id) {
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

            if (category.getImageUrl() == null || category.getImageUrl().isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", "Category has no image to delete",
                        "categoryId", id
                ));
            }

            String oldImageUrl = category.getImageUrl();

            // Delete from Cloudinary first
            boolean deletedFromCloudinary = cloudinaryService.deleteImageByUrl(oldImageUrl);

            if (deletedFromCloudinary) {
                log.info("Category {} image deleted from Cloudinary: {}", id, oldImageUrl);
            } else {
                log.warn("Failed to delete category {} image from Cloudinary, but will remove from database", id);
            }

            // Remove image URL from category database
            category.setImageUrl(null);
            categoryRepository.save(category);

            log.info("Category {} image removed from database: {}", id, oldImageUrl);

            return ResponseEntity.ok(Map.of(
                    "message", "Category image deleted successfully" +
                               (deletedFromCloudinary ? " from both Cloudinary and database" : " from database only"),
                    "categoryId", id,
                    "deletedFromCloudinary", deletedFromCloudinary
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to delete category image: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete category image"));
        }
    }
}
