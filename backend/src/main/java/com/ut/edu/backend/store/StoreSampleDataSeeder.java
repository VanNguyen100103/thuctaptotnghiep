package com.ut.edu.backend.store;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Seeds a freshly onboarded store with a few demo categories and products so
 * the owner's dashboard and storefront are not empty on first login.
 * Slugs/SKUs are only unique per store (unique (store_id, slug)), so every
 * store can safely receive the same sample set.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoreSampleDataSeeder {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public void seed(Store store) {
        Category ao = categoryRepository.save(Category.builder()
                .store(store)
                .name("Áo")
                .slug("ao")
                .description("Danh mục mẫu — sửa hoặc xóa tùy ý")
                .displayOrder(0)
                .build());

        Category quan = categoryRepository.save(Category.builder()
                .store(store)
                .name("Quần")
                .slug("quan")
                .description("Danh mục mẫu — sửa hoặc xóa tùy ý")
                .displayOrder(1)
                .build());

        List<Product> samples = List.of(
                sample(store, "Áo Thun Basic (mẫu)", "ao-thun-basic-mau", "DEMO-001",
                        new BigDecimal("199000"), 50, "Unisex", ao),
                sample(store, "Áo Sơ Mi Trắng (mẫu)", "ao-so-mi-trang-mau", "DEMO-002",
                        new BigDecimal("349000"), 30, "Men", ao),
                sample(store, "Quần Jean Slim (mẫu)", "quan-jean-slim-mau", "DEMO-003",
                        new BigDecimal("499000"), 40, "Men", quan),
                sample(store, "Quần Short Thể Thao (mẫu)", "quan-short-the-thao-mau", "DEMO-004",
                        new BigDecimal("179000"), 60, "Unisex", quan)
        );
        productRepository.saveAll(samples);

        log.info("Seeded sample data for store {} ({} categories, {} products)",
                store.getSlug(), 2, samples.size());
    }

    private Product sample(Store store, String name, String slug, String sku,
                           BigDecimal price, int stock, String gender, Category category) {
        Product product = Product.builder()
                .store(store)
                .name(name)
                .slug(slug)
                .sku(sku)
                .shortDescription("Sản phẩm mẫu được tạo sẵn khi đăng ký cửa hàng")
                .description("Đây là sản phẩm mẫu để cửa hàng của bạn không trống khi mới tạo. "
                        + "Bạn có thể sửa thông tin, thay ảnh hoặc xóa sản phẩm này trong dashboard.")
                .price(price)
                .stockQuantity(stock)
                .gender(gender)
                .availableSizes(Set.of("S", "M", "L", "XL"))
                .build();
        product.addCategory(category);
        return product;
    }
}
