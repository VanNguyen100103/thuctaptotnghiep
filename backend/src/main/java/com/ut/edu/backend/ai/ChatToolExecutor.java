package com.ut.edu.backend.ai;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.policy.StorePolicyRepository;
import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.product.ProductService;
import com.ut.edu.backend.store.TenantGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Executes the 4 tools in AiToolCatalog as live, tenant-scoped DB reads - this
 * is what makes the chat assistant's product/policy knowledge real-time
 * (no re-indexing, no embeddings: every call hits the current data).
 * Deliberately read-only: no method here may reach a mutating service call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatToolExecutor {

    private static final int MAX_RESULTS = 8;
    private static final int MAX_POLICY_RESULTS = 10;
    private static final int MAX_POLICY_CONTENT_LENGTH = 1000;

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StorePolicyRepository storePolicyRepository;
    private final TenantGuard tenantGuard;

    public Object execute(String toolName, Map<String, Object> args) {
        try {
            return switch (toolName) {
                case "search_products" -> searchProducts(args);
                case "get_product_by_id" -> getProductById(args);
                case "list_categories" -> listCategories();
                case "get_store_policies" -> getStorePolicies(args);
                default -> Map.of("error", "Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.warn("Tool execution failed: {} args={}", toolName, args, e);
            return Map.of("error", "Tool execution failed: " + e.getMessage());
        }
    }

    private List<AiProductSummary> searchProducts(Map<String, Object> args) {
        String keyword = str(args.get("keyword"));
        String categoryName = str(args.get("categoryName"));
        BigDecimal minPrice = bigDecimal(args.get("minPrice"));
        BigDecimal maxPrice = bigDecimal(args.get("maxPrice"));
        String brand = str(args.get("brand"));
        Boolean inStock = bool(args.get("inStock"));

        Long categoryId = null;
        if (categoryName != null) {
            categoryId = categoryRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                    .map(Category::getId)
                    .findFirst()
                    .orElse(null);
        }

        // "newest" if unset/unrecognized - the sort label is also passed through as
        // ProductService's cache-key discriminator, so e.g. a price_desc search never
        // collides in Redis with a bestselling search that has identical other filters.
        String sortLabel = normalizeSortLabel(str(args.get("sortBy")));
        Pageable pageable = PageRequest.of(0, MAX_RESULTS, resolveSort(sortLabel));
        Page<Product> page = productService.searchProducts(
                keyword, categoryId, minPrice, maxPrice, brand, null, null, null, sortLabel, inStock, pageable);
        return page.getContent().stream().map(AiProductSummary::from).toList();
    }

    private static String normalizeSortLabel(String sortBy) {
        return switch (sortBy == null ? "" : sortBy) {
            case "price_desc", "price_asc", "bestselling" -> sortBy;
            default -> "newest";
        };
    }

    private static Sort resolveSort(String sortLabel) {
        return switch (sortLabel) {
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "bestselling" -> Sort.by(Sort.Direction.DESC, "soldCount");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private Object getProductById(Map<String, Object> args) {
        Long id = longVal(args.get("productId"));
        if (id == null) {
            return Map.of("error", "productId is required");
        }
        return productRepository.findById(id)
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .filter(p -> tenantGuard.isCurrentStore(p.getStore()))
                .<Object>map(AiProductSummary::from)
                .orElse(Map.of("error", "Product not found"));
    }

    private List<Map<String, String>> listCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(c -> Map.of("name", c.getName(), "description", c.getDescription() == null ? "" : c.getDescription()))
                .toList();
    }

    private List<Map<String, String>> getStorePolicies(Map<String, Object> args) {
        String topic = str(args.get("topic"));
        return storePolicyRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .filter(p -> topic == null || topic.isBlank()
                        || p.getTitle().toLowerCase().contains(topic.toLowerCase())
                        || p.getContent().toLowerCase().contains(topic.toLowerCase()))
                .limit(MAX_POLICY_RESULTS)
                .map(p -> Map.of("title", p.getTitle(), "content", truncate(p.getContent())))
                .toList();
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_POLICY_CONTENT_LENGTH ? s : s.substring(0, MAX_POLICY_CONTENT_LENGTH) + "...";
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString();
        return s.isBlank() ? null : s;
    }

    private static Long longVal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal bigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(o.toString());
    }
}
