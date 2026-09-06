package com.ut.edu.backend.ai;

import java.util.List;
import java.util.Map;

/**
 * The 4 read-only tools the storefront chat assistant may call. Deliberately
 * generic - search_products/list_categories/get_store_policies work the same
 * way for every industry (fashion, F&B, beauty, ...) since they read straight
 * from Product/Category/StorePolicy, none of which are vertical-specific.
 * Every tool is read-only by construction - none of them may reach a
 * mutating service method (see ChatToolExecutor).
 */
public final class AiToolCatalog {

    private AiToolCatalog() {
    }

    public static final List<AiTool> TOOLS = List.of(
            new AiTool(
                    "search_products",
                    "Tìm kiếm sản phẩm đang có trong cửa hàng theo từ khóa, danh mục, khoảng giá, thương hiệu hoặc còn hàng hay không. "
                            + "Luôn dùng tool này khi khách hỏi về sản phẩm cụ thể hoặc muốn xem danh sách sản phẩm - không tự bịa sản phẩm.",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "keyword", Map.of("type", "string", "description", "Từ khóa tìm kiếm (tên, mô tả, thương hiệu)"),
                                    "categoryName", Map.of("type", "string", "description", "Tên danh mục sản phẩm, nếu khách chỉ định"),
                                    "minPrice", Map.of("type", "number", "description", "Giá tối thiểu (VND)"),
                                    "maxPrice", Map.of("type", "number", "description", "Giá tối đa (VND)"),
                                    "brand", Map.of("type", "string", "description", "Thương hiệu"),
                                    "inStock", Map.of("type", "boolean", "description", "true nếu chỉ muốn sản phẩm còn hàng"),
                                    "sortBy", Map.of(
                                            "type", "string",
                                            "description", "Cách sắp xếp kết quả - LUÔN chỉ định khi khách hỏi kiểu \"...nhất\": "
                                                    + "'price_desc' cho đắt nhất/giá cao nhất, 'price_asc' cho rẻ nhất/giá thấp nhất, "
                                                    + "'bestselling' cho bán chạy nhất, 'newest' cho mới nhất (mặc định nếu bỏ trống). "
                                                    + "Kết quả trả về đã sắp xếp sẵn - sản phẩm đầu tiên trong danh sách chính là câu trả lời đúng.",
                                            "enum", List.of("newest", "price_desc", "price_asc", "bestselling")
                                    )
                            )
                    )
            ),
            new AiTool(
                    "get_product_by_id",
                    "Lấy thông tin chi tiết, giá và tồn kho hiện tại của một sản phẩm theo id. "
                            + "Dùng khi đã biết id sản phẩm (thường từ kết quả search_products trước đó).",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "productId", Map.of("type", "integer", "description", "id của sản phẩm")
                            ),
                            "required", List.of("productId")
                    )
            ),
            new AiTool(
                    "list_categories",
                    "Liệt kê các danh mục sản phẩm hiện có trong cửa hàng.",
                    Map.of("type", "object", "properties", Map.of())
            ),
            new AiTool(
                    "get_store_policies",
                    "Lấy các chính sách của cửa hàng (đổi trả, vận chuyển, bảo hành, thanh toán, ...) do chủ cửa hàng tự đặt ra. "
                            + "Luôn dùng tool này khi khách hỏi về chính sách - không tự suy đoán chính sách.",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "topic", Map.of("type", "string", "description",
                                            "Từ khóa lọc chính sách liên quan, ví dụ 'đổi trả', 'vận chuyển' (bỏ trống để lấy tất cả)")
                            )
                    )
            )
    );
}
