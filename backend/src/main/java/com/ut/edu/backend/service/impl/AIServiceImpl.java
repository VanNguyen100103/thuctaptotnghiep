package com.ut.edu.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.edu.backend.dto.*;
import com.ut.edu.backend.model.Category;
import com.ut.edu.backend.model.Product;
import com.ut.edu.backend.model.User;
import com.ut.edu.backend.repository.ProductRepository;
import com.ut.edu.backend.repository.UserRepository;
import com.ut.edu.backend.service.inter.IAIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Service Implementation using DeepSeek API (OpenAI-compatible chat completions)
 * All business AI features (recommendations, clustering, analysis) build prompts here
 * and delegate the actual model call to DeepSeekServiceImpl
 */
@Service
@Primary
@Slf4j
public class AIServiceImpl implements IAIService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final com.ut.edu.backend.repository.OrderRepository orderRepository;
    private final com.ut.edu.backend.repository.ReviewRepository reviewRepository;
    private final DeepSeekServiceImpl deepSeekService;

    public AIServiceImpl(ProductRepository productRepository,
                                UserRepository userRepository,
                                RestTemplate restTemplate,
                                com.ut.edu.backend.repository.OrderRepository orderRepository,
                                com.ut.edu.backend.repository.ReviewRepository reviewRepository,
                                DeepSeekServiceImpl deepSeekService) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
        this.deepSeekService = deepSeekService;
    }

    /**
     * Call AI provider (DeepSeek only)
     */
    private String callAI(String prompt) {
        if (!deepSeekService.isAvailable()) {
            log.warn("DeepSeek service not configured (DEEPSEEK_API_KEY missing)");
            return "Xin lỗi, tôi đang gặp sự cố kỹ thuật. Vui lòng thử lại sau.";
        }
        try {
            return deepSeekService.callDeepSeek(prompt);
        } catch (Exception e) {
            log.error("❌ DeepSeek API failed: {}", e.getMessage());
            return "Xin lỗi, tôi đang gặp sự cố kỹ thuật. Vui lòng thử lại sau.";
        }
    }

    @Override
    @Cacheable(value = "ai-recommendations", key = "#userId + '-' + #limit", cacheManager = "caffeineCacheManager")
    public AIRecommendationResponse getPersonalizedRecommendationsWithExplanation(Long userId, int limit) {
        log.info("Getting AI-powered personalized recommendations with explanation for user: {} (CACHE MISS - calling DeepSeek)", userId);

        // Get user data
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User {} not found", userId);
            AIRecommendationResponse response = new AIRecommendationResponse();
            response.setProducts(Collections.emptyList());
            response.setAiExplanation("Không tìm thấy thông tin người dùng.");
            response.setPrompt("");
            response.setTotalRecommended(0);
            return response;
        }

        User user = userOpt.get();

        // Get all available products
        List<Product> allProducts = productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .collect(Collectors.toList());

        // Build comprehensive product info for DeepSeek
        StringBuilder productInfo = new StringBuilder();
        for (int i = 0; i < Math.min(allProducts.size(), 50); i++) {
            Product p = allProducts.get(i);
            productInfo.append(String.format("ID:%d|Tên:%s|Brand:%s|Danh mục:%s|Giá:%s VNĐ|Rating:%.1f|Reviews:%d|Gender:%s|Màu sắc:%s\n",
                    p.getId(),
                    p.getName(),
                    p.getBrand() != null ? p.getBrand() : "N/A",
                    p.getCategories().stream().map(Category::getName).collect(Collectors.joining(",")),
                    formatPrice(p.getPrice()),
                    p.getAverageRating() != null ? p.getAverageRating().doubleValue() : 0.0,
                    p.getReviewCount() != null ? p.getReviewCount() : 0,
                    p.getGender() != null ? p.getGender() : "Unisex",
                    p.getAvailableColors() != null && !p.getAvailableColors().isEmpty()
                        ? String.join(",", p.getAvailableColors()) : "N/A"
            ));
        }

        // Build user preference info
        StringBuilder userPreferences = new StringBuilder();
        userPreferences.append("Lịch sử review của user:\n");
        userPreferences.append("(User đã review các sản phẩm với rating trung bình, cho thấy họ quan tâm đến chất lượng)\n");

        String prompt = String.format(
            "Bạn là AI chuyên gia đề xuất sản phẩm thời trang dựa trên Machine Learning và phân tích hành vi.\n\n" +
            "=== THÔNG TIN NGƯỜI DÙNG ===\n" +
            "- User ID: %d\n" +
            "- Họ tên: %s %s\n" +
            "- Email: %s\n" +
            "%s\n" +
            "=== DANH SÁCH SẢN PHẨM CÓ SẴN (TOP 50) ===\n%s\n" +
            "=== NHIỆM VỤ ===\n" +
            "Phân tích và chọn %d sản phẩm PHÙ HỢP NHẤT cho người dùng này.\n\n" +
            "=== TIÊU CHÍ ĐÁNH GIÁ (ƯU TIÊN GIẢM DẦN) ===\n" +
            "1. CHẤT LƯỢNG: Ưu tiên sản phẩm có Rating >= 4.0 và có nhiều reviews\n" +
            "2. ĐA DẠNG: Chọn từ nhiều danh mục khác nhau (áo, quần, phụ kiện...) để đa dạng\n" +
            "3. PHONG CÁCH: Phù hợp với gender preferences (nếu có)\n" +
            "4. GIÁ CẢ: Cân bằng giữa cao cấp (premium) và bình dân (affordable)\n" +
            "5. TRENDING: Sản phẩm có brand nổi tiếng hoặc màu sắc đang hot trend\n" +
            "6. VALUE FOR MONEY: Ưu tiên sản phẩm có discount %% cao (compareAtPrice vs price)\n\n" +
            "=== YÊU CẦU ĐẦU RA ===\n" +
            "Trả về JSON format:\n" +
            "{\n" +
            "  \"productIds\": [2, 1, 5, 8, 12],\n" +
            "  \"explanation\": \"Tôi đã chọn các sản phẩm này vì... (2-3 câu giải thích ngắn gọn)\"\n" +
            "}\n" +
            "CHỈ TRẢ VỀ JSON, KHÔNG CÓ TEXT KHÁC.",
            userId,
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            userPreferences.toString(),
            productInfo.toString(),
            limit
        );

        String aiResponse = callAI(prompt);

        // Parse JSON response
        try {
            // Clean response - remove markdown code blocks if present
            String cleanedResponse = aiResponse.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            }
            if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();

            JsonNode responseNode = objectMapper.readTree(cleanedResponse);

            // Extract product IDs
            List<Long> recommendedIds = new ArrayList<>();
            JsonNode idsNode = responseNode.path("productIds");
            if (idsNode.isArray()) {
                for (JsonNode idNode : idsNode) {
                    recommendedIds.add(idNode.asLong());
                }
            }

            // Extract explanation
            String explanation = responseNode.path("explanation").asText("AI đã phân tích và chọn sản phẩm phù hợp nhất cho bạn.");

            // Fetch products
            List<Product> recommendations = Collections.emptyList();
            if (!recommendedIds.isEmpty()) {
                recommendations = productRepository.findAllById(recommendedIds).stream()
                        .filter(p -> p.getStockQuantity() > 0)
                        .limit(limit)
                        .collect(Collectors.toList());
            }

            AIRecommendationResponse response = new AIRecommendationResponse();
            response.setProducts(recommendations);
            response.setAiExplanation(explanation);
            response.setPrompt(prompt);
            response.setTotalRecommended(recommendations.size());

            log.info("AI successfully recommended {} products with explanation for user {}", recommendations.size(), userId);
            return response;

        } catch (Exception e) {
            log.error("Error parsing AI response JSON", e);

            // Fallback: try to parse as simple ID list
            List<Long> recommendedIds = parseProductIds(aiResponse);
            List<Product> recommendations = Collections.emptyList();
            if (!recommendedIds.isEmpty()) {
                recommendations = productRepository.findAllById(recommendedIds).stream()
                        .filter(p -> p.getStockQuantity() > 0)
                        .limit(limit)
                        .collect(Collectors.toList());
            }

            AIRecommendationResponse response = new AIRecommendationResponse();
            response.setProducts(recommendations);
            response.setAiExplanation("AI đã phân tích và chọn sản phẩm dựa trên rating, đa dạng danh mục và giá trị.");
            response.setPrompt(prompt);
            response.setTotalRecommended(recommendations.size());
            return response;
        }
    }

    @Override
    public List<Product> getSimilarProducts(Long productId, int limit) {
        log.info("Finding AI-powered similar products for: {}", productId);

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return Collections.emptyList();
        }

        Product targetProduct = productOpt.get();

        // Get candidate products (same category or similar price range)
        List<Product> candidates = productRepository.findAll().stream()
                .filter(p -> !p.getId().equals(productId))
                .filter(p -> p.getStockQuantity() > 0)
                .limit(50)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // Build product info for DeepSeek
        StringBuilder productInfo = new StringBuilder();
        for (Product p : candidates) {
            productInfo.append(String.format("ID:%d|%s|%s|%s VNĐ|Brand:%s\n",
                    p.getId(),
                    p.getName(),
                    p.getCategories().stream().map(Category::getName).collect(Collectors.joining(",")),
                    formatPrice(p.getPrice()),
                    p.getBrand() != null ? p.getBrand() : "N/A"
            ));
        }

        String prompt = String.format(
            "Bạn là AI chuyên gia tìm sản phẩm tương tự.\n\n" +
            "Sản phẩm gốc:\n" +
            "- Tên: %s\n" +
            "- Danh mục: %s\n" +
            "- Giá: %s VNĐ\n" +
            "- Thương hiệu: %s\n\n" +
            "Danh sách sản phẩm ứng viên:\n%s\n" +
            "Nhiệm vụ: Tìm %d sản phẩm TƯƠNG TỰ NHẤT dựa trên:\n" +
            "1. Cùng danh mục hoặc danh mục liên quan\n" +
            "2. Khoảng giá tương đương (±30%%)\n" +
            "3. Cùng thương hiệu hoặc phong cách tương tự\n\n" +
            "Trả về DANH SÁCH ID sản phẩm, phân tách bằng dấu phẩy (VD: 3,7,12,18,22).\n" +
            "CHỈ TRẢ VỀ CÁC SỐ ID, KHÔNG CẦN GIẢI THÍCH.",
            targetProduct.getName(),
            targetProduct.getCategories().stream().map(Category::getName).collect(Collectors.joining(", ")),
            formatPrice(targetProduct.getPrice()),
            targetProduct.getBrand() != null ? targetProduct.getBrand() : "N/A",
            productInfo.toString(),
            limit
        );

        String aiResponse = callAI(prompt);
        List<Long> similarIds = parseProductIds(aiResponse);

        if (!similarIds.isEmpty()) {
            List<Product> similar = productRepository.findAllById(similarIds).stream()
                    .filter(p -> p.getStockQuantity() > 0)
                    .limit(limit)
                    .collect(Collectors.toList());

            if (!similar.isEmpty()) {
                return similar;
            }
        }

        // Fallback: simple category matching
        log.warn("AI similar products parsing failed, using fallback");
        return candidates.stream()
                .filter(p -> {
                    Set<Long> targetCategoryIds = targetProduct.getCategories().stream()
                            .map(Category::getId)
                            .collect(Collectors.toSet());
                    Set<Long> pCategoryIds = p.getCategories().stream()
                            .map(Category::getId)
                            .collect(Collectors.toSet());
                    return targetCategoryIds.stream().anyMatch(pCategoryIds::contains);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public String chatWithBot(String userMessage, Long userId) {
        log.info("DeepSeek AI chatbot - User {}: {}", userId, userMessage);

        try {
            String normalizedMessage = normalizeVietnamese(userMessage.toLowerCase());

            // Get product data from database, then use DeepSeek AI to format response
            List<Product> products = null;
            String context = "";

            // 1. Price queries
            if (normalizedMessage.contains("gia") &&
                (normalizedMessage.contains("cao nhat") || normalizedMessage.contains("thap nhat"))) {
                products = getPriceQueryProducts(normalizedMessage);
                context = normalizedMessage.contains("cao nhat") ? "sản phẩm có giá CAO NHẤT" : "sản phẩm có giá THẤP NHẤT";
            }
            // 2. Best selling
            else if (normalizedMessage.contains("ban chay") || normalizedMessage.contains("best selling") ||
                normalizedMessage.contains("hot") || normalizedMessage.contains("pho bien")) {
                products = getBestSellingProducts(normalizedMessage);
                context = "sản phẩm BÁN CHẠY nhất";
            }
            // 3. New arrivals
            else if (normalizedMessage.contains("hang moi") || normalizedMessage.contains("new arrival") ||
                normalizedMessage.contains("moi ve") || normalizedMessage.contains("moi nhat")) {
                products = getNewArrivalsProducts(normalizedMessage);
                context = "sản phẩm MỚI VỀ";
            }
            // 4. Category search
            else if (normalizedMessage.contains("ao") || normalizedMessage.contains("quan") ||
                normalizedMessage.contains("vay") || normalizedMessage.contains("dam")) {
                products = getCategoryProducts(normalizedMessage);
                String category = extractCategory(normalizedMessage);
                context = "sản phẩm thuộc danh mục " + (category != null ? category : "");
            }
            // 5. General search
            else if (normalizedMessage.contains("tim") || normalizedMessage.contains("co") ||
                normalizedMessage.contains("san pham")) {
                products = getGeneralProducts();
                context = "các sản phẩm hiện có";
            }

            // If found products, use DeepSeek AI to format naturally
            if (products != null) {
                if (products.isEmpty()) {
                    return callAI(String.format(
                        "Khách hàng hỏi: '%s'\n" +
                        "Hiện không tìm thấy sản phẩm nào.\n" +
                        "Hãy trả lời thân thiện và gợi ý khách xem các sản phẩm khác (2-3 câu).",
                        userMessage
                    ));
                }
                return formatProductsWithDeepSeek(products, context, userMessage);
            }

            // General conversation - use DeepSeek AI
            return handleGeneralConversation(userMessage);

        } catch (Exception e) {
            log.error("Error in chatbot", e);
            return "Xin lỗi, tôi đang gặp sự cố. Vui lòng thử lại sau.";
        }
    }

    /**
     * Format product list using DeepSeek AI for natural, conversational response
     */
    private String formatProductsWithDeepSeek(List<Product> products, String context, String userMessage) {
        // Build product info
        StringBuilder productInfo = new StringBuilder();
        for (int i = 0; i < Math.min(products.size(), 5); i++) {
            Product p = products.get(i);
            productInfo.append(String.format("%d. %s - %s VNĐ",
                    i + 1,
                    p.getName(),
                    formatPrice(p.getPrice())
            ));
            if (p.getAverageRating() != null && p.getAverageRating().compareTo(BigDecimal.ZERO) > 0) {
                productInfo.append(String.format(" (⭐ %.1f/5)", p.getAverageRating().doubleValue()));
            }
            productInfo.append("\n");
        }

        String prompt = String.format(
            "Bạn là trợ lý bán hàng thời trang thân thiện.\n\n" +
            "Khách hàng hỏi: '%s'\n" +
            "Context: Tìm thấy %s\n\n" +
            "Danh sách sản phẩm:\n%s\n" +
            "Hãy giới thiệu các sản phẩm này một cách TỰ NHIÊN, THÂN THIỆN như đang tư vấn trực tiếp.\n" +
            "- Đừng liệt kê như list, hãy nói như đang trò chuyện\n" +
            "- Nhấn mạnh điểm nổi bật (giá, rating)\n" +
            "- Tối đa 3-4 câu ngắn gọn",
            userMessage,
            context,
            productInfo.toString()
        );

        return callAI(prompt);
    }

    private List<Product> getPriceQueryProducts(String message) {
        String category = extractCategory(message);
        String gender = extractGender(message);
        boolean isHighest = message.contains("cao nhat") || message.contains("dat nhat");

        return productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .filter(p -> category == null || matchesCategory(p, category))
                .filter(p -> gender == null || matchesGender(p, gender))
                .sorted((p1, p2) -> isHighest
                        ? p2.getPrice().compareTo(p1.getPrice())
                        : p1.getPrice().compareTo(p2.getPrice()))
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<Product> getBestSellingProducts(String message) {
        String category = extractCategory(message);
        String gender = extractGender(message);

        return productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .filter(p -> category == null || matchesCategory(p, category))
                .filter(p -> gender == null || matchesGender(p, gender))
                .sorted((p1, p2) -> {
                    BigDecimal rating1 = p1.getAverageRating() != null ? p1.getAverageRating() : BigDecimal.ZERO;
                    BigDecimal rating2 = p2.getAverageRating() != null ? p2.getAverageRating() : BigDecimal.ZERO;
                    return rating2.compareTo(rating1);
                })
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<Product> getNewArrivalsProducts(String message) {
        String category = extractCategory(message);
        String gender = extractGender(message);
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minus(30, ChronoUnit.DAYS);

        return productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(thirtyDaysAgo))
                .filter(p -> category == null || matchesCategory(p, category))
                .filter(p -> gender == null || matchesGender(p, gender))
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());
    }

    

    private List<Product> getCategoryProducts(String message) {
        String category = extractCategory(message);
        String gender = extractGender(message);

        if (category == null) {
            return Collections.emptyList();
        }

        return productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .filter(p -> matchesCategory(p, category))
                .filter(p -> gender == null || matchesGender(p, gender))
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<Product> getGeneralProducts() {
        return productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .limit(10)
                .collect(Collectors.toList());
    }

    private String handleGeneralConversation(String userMessage) {
        String prompt = String.format(
            "Bạn là trợ lý AI thông minh của cửa hàng thời trang E-commerce Fashion Store.\n" +
            "Nhiệm vụ: Trả lời câu hỏi của khách hàng một cách thân thiện, chuyên nghiệp.\n\n" +
            "Câu hỏi của khách hàng: %s\n\n" +
            "Hướng dẫn:\n" +
            "- Trả lời ngắn gọn, rõ ràng bằng tiếng Việt (2-3 câu)\n" +
            "- Nếu hỏi về sản phẩm cụ thể, đề xuất khách hàng xem trang sản phẩm\n" +
            "- Nếu hỏi về đơn hàng, hướng dẫn kiểm tra trong tài khoản\n" +
            "- Luôn thân thiện và hỗ trợ tối đa",
            userMessage
        );

        return callAI(prompt);
    }

    private String extractCategory(String message) {
        String normalized = normalizeVietnamese(message);
        if (normalized.contains("ao")) return "ao";
        if (normalized.contains("quan")) return "quan";
        if (normalized.contains("vay")) return "vay";
        if (normalized.contains("dam")) return "dam";
        return null;
    }

    private String extractGender(String message) {
        String normalized = normalizeVietnamese(message);
        if (normalized.contains("nam") && !normalized.contains("nu")) return "nam";
        if (normalized.contains("nu") || normalized.contains("women")) return "nu";
        if (normalized.contains("unisex")) return "unisex";
        return null;
    }

    private boolean matchesCategory(Product product, String categoryKeyword) {
        if (categoryKeyword == null) return true;
        return product.getCategories().stream()
                .anyMatch(c -> normalizeVietnamese(c.getName().toLowerCase()).contains(categoryKeyword));
    }

    private boolean matchesGender(Product product, String gender) {
        if (gender == null) return true;
        return product.getCategories().stream()
                .anyMatch(c -> normalizeVietnamese(c.getName().toLowerCase()).contains(gender));
    }

    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        String normalized = text;
        normalized = normalized.replaceAll("[áàảãạăắằẳẵặâấầẩẫậ]", "a");
        normalized = normalized.replaceAll("[éèẻẽẹêếềểễệ]", "e");
        normalized = normalized.replaceAll("[íìỉĩị]", "i");
        normalized = normalized.replaceAll("[óòỏõọôốồổỗộơớờởỡợ]", "o");
        normalized = normalized.replaceAll("[úùủũụưứừửữự]", "u");
        normalized = normalized.replaceAll("[ýỳỷỹỵ]", "y");
        normalized = normalized.replaceAll("đ", "d");
        return normalized;
    }

    private String formatPrice(BigDecimal price) {
        return String.format("%,d", price.longValue());
    }

    /**
     * Helper to parse product IDs from DeepSeek response
     */
    private List<Long> parseProductIds(String response) {
        List<Long> ids = new ArrayList<>();
        try {
            // Remove any non-numeric characters except commas
            String cleaned = response.replaceAll("[^0-9,]", "");
            String[] parts = cleaned.split(",");
            for (String part : parts) {
                if (!part.trim().isEmpty()) {
                    try {
                        ids.add(Long.parseLong(part.trim()));
                    } catch (NumberFormatException e) {
                        // Skip invalid numbers
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing product IDs from: {}", response, e);
        }
        return ids;
    }


    @Override
    public AISemanticSearchResponse smartSearch(String query, int limit) {
        log.info("AI-powered smart search for: {}", query);

        // Get all products with full details
        List<Product> allProducts = productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .limit(100)
                .collect(Collectors.toList());

        if (allProducts.isEmpty()) {
            return new AISemanticSearchResponse(query, Collections.emptyList(), "Không tìm thấy sản phẩm nào.", "", 0);
        }

        // Build detailed product info for AI analysis
        StringBuilder productInfo = new StringBuilder();
        for (Product p : allProducts) {
            productInfo.append(String.format("ID:%d|Tên:%s|Giá:%s VNĐ|Brand:%s|Danh mục:%s|Mô tả:%s\n",
                    p.getId(),
                    p.getName(),
                    formatPrice(p.getPrice()),
                    p.getBrand() != null ? p.getBrand() : "N/A",
                    p.getCategories().stream().map(Category::getName).collect(Collectors.joining(",")),
                    p.getDescription() != null ? p.getDescription().substring(0, Math.min(150, p.getDescription().length())) : ""
            ));
        }

        String prompt = String.format(
            "Bạn là AI SMART SEARCH thông minh.\n\n" +
            "Truy vấn phức tạp của khách hàng: '%s'\n\n" +
            "YÊU CẦU: Phân tích truy vấn và tìm sản phẩm PHÙ HỢP với TẤT CẢ tiêu chí:\n" +
            "- DANH MỤC/LOẠI: áo, quần, váy, giày... (VD: 'áo nam' → tìm trong Áo Nam)\n" +
            "- ĐẶC ĐIỂM/TÍNH NĂNG: chống nước, thấm hút mồ hôi, dự tiệc... (từ mô tả)\n" +
            "- GIÁ: 500k, dưới 1 triệu, giá rẻ... (phân tích khoảng giá)\n" +
            "- THƯƠNG HIỆU: Nike, Adidas... (nếu có)\n" +
            "- MÀU SẮC: đỏ, xanh, đen... (từ tên/mô tả)\n\n" +
            "Danh sách sản phẩm:\n%s\n" +
            "CHỈ TRẢ VỀ DANH SÁCH ID sản phẩm (tối đa %d sản phẩm), phân tách bằng dấu phẩy.\n" +
            "KHÔNG GIẢI THÍCH, CHỈ GHI CÁC SỐ ID.\n\n" +
            "VÍ DỤ truy vấn: 'áo nam chống nước giá 500k'\n" +
            "→ Tìm sản phẩm: (1) Thuộc Áo Nam, (2) Mô tả có 'chống nước' hoặc 'không thấm', (3) Giá khoảng 400k-600k",
            query,
            productInfo.toString(),
            limit
        );

        String aiResponse = callAI(prompt);
        List<Long> resultIds = parseProductIds(aiResponse);

        List<Product> results = Collections.emptyList();
        if (!resultIds.isEmpty()) {
            results = productRepository.findAllById(resultIds).stream()
                    .filter(p -> p.getStockQuantity() > 0)
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        // If AI failed, fallback to simple text matching
        if (results.isEmpty()) {
            log.warn("AI smart search parsing failed, using fallback");
            String normalizedQuery = normalizeVietnamese(query.toLowerCase());
            results = allProducts.stream()
                    .filter(p -> {
                        String productText = normalizeVietnamese(
                            (p.getName() + " " + (p.getDescription() != null ? p.getDescription() : "")).toLowerCase()
                        );
                        return productText.contains(normalizedQuery);
                    })
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        // Generate explanation
        StringBuilder context = new StringBuilder();
        context.append(String.format("Truy vấn: \"%s\"\n\n", query));
        context.append(String.format("Tìm thấy %d sản phẩm phù hợp:\n", results.size()));

        for (int i = 0; i < results.size(); i++) {
            Product p = results.get(i);
            context.append(String.format("%d. %s - %s VNĐ - %s\n",
                i + 1,
                p.getName(),
                formatPrice(p.getPrice()),
                p.getCategories().stream().map(Category::getName).collect(Collectors.joining(", "))
            ));
        }

        String explainPrompt = String.format(
            "Bạn là AI chuyên gia Smart Search.\n\n%s\n" +
            "YÊU CẦU: GIẢI THÍCH cụ thể tại sao các sản phẩm này PHÙ HỢP với truy vấn '%s'.\n\n" +
            "BẮT BUỘC đề cập:\n" +
            "1. PHÂN TÍCH TRUY VẤN: Khách hàng tìm gì? (VD: \"Khách cần áo nam, có tính năng chống nước, giá khoảng 500k\")\n" +
            "2. SẢN PHẨM KHỚP: Sản phẩm nào đáp ứng yêu cầu? (Nêu TÊN và GIÁ cụ thể)\n" +
            "3. MỨC ĐỘ PHÙ HỢP: Có khớp 100%% hay chỉ một phần?\n\n" +
            "Format: 2-3 câu CỤ THỂ bằng tiếng Việt.\n" +
            "VÍ DỤ: \"Khách hàng tìm áo nam chống nước giá 500k. Tìm thấy 'Áo khoác dù nam' (599k) có tính năng chống thấm nước, phù hợp 90%% với yêu cầu. Giá cao hơn một chút nhưng chất lượng tốt.\"",
            context.toString(),
            query
        );

        String explanation = callAI(explainPrompt);

        return new AISemanticSearchResponse(query, results, explanation, prompt, results.size());
    }

    @Override
    public Map<String, List<Product>> clusterProducts(List<Product> products, int numClusters) {
        log.info("AI-powered product clustering");

        // Build product info for DeepSeek
        StringBuilder productInfo = new StringBuilder();
        for (Product p : products) {
            productInfo.append(String.format("ID:%d|%s|%s|%s VNĐ\n",
                    p.getId(),
                    p.getName(),
                    p.getCategories().stream().map(Category::getName).collect(Collectors.joining(",")),
                    formatPrice(p.getPrice())
            ));
        }

        String prompt = String.format(
            "Bạn là AI phân cụm sản phẩm thông minh.\n\n" +
            "Danh sách sản phẩm:\n%s\n" +
            "Nhiệm vụ: Phân %d sản phẩm này thành %d cụm (cluster) dựa trên:\n" +
            "1. Danh mục sản phẩm\n" +
            "2. Khoảng giá\n" +
            "3. Phong cách/đặc điểm chung\n\n" +
            "Format trả về:\n" +
            "ClusterName1: id1,id2,id3\n" +
            "ClusterName2: id4,id5,id6\n" +
            "...\n\n" +
            "Tên cluster nên MÔ TẢ đặc điểm chung (VD: 'Áo thời trang cao cấp', 'Quần giá rẻ')",
            productInfo.toString(),
            products.size(),
            numClusters
        );

        String aiResponse = callAI(prompt);

        // Parse AI response into clusters
        Map<String, List<Product>> clusters = parseProductClusters(aiResponse, products);

        if (!clusters.isEmpty()) {
            return clusters;
        }

        // Fallback: simple category grouping
        log.warn("AI clustering parsing failed, using fallback");
        return products.stream()
                .collect(Collectors.groupingBy(p ->
                    p.getCategories().isEmpty() ? "Khác" : p.getCategories().iterator().next().getName()
                ));
    }

    /**
     * Helper to parse cluster response from DeepSeek
     */
    private Map<String, List<Product>> parseProductClusters(String response, List<Product> allProducts) {
        Map<String, List<Product>> clusters = new HashMap<>();
        Map<Long, Product> productMap = allProducts.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String clusterName = parts[0].trim();
                    String idsStr = parts[1].trim();

                    List<Long> ids = parseProductIds(idsStr);
                    List<Product> clusterProducts = ids.stream()
                            .map(productMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (!clusterProducts.isEmpty()) {
                        clusters.put(clusterName, clusterProducts);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing clusters from: {}", response, e);
        }

        return clusters;
    }

    @Override
    public List<Product> predictTrendingProducts(int limit) {
        log.info("AI-powered trending products prediction");

        // Get candidate products (high rating, recent)
        List<Product> candidates = productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .filter(p -> p.getAverageRating() != null && p.getAverageRating().compareTo(new BigDecimal("3.5")) >= 0)
                .limit(50)
                .collect(Collectors.toList());

        // If no products with rating >= 3.5, get any products with stock
        if (candidates.isEmpty()) {
            log.warn("No products with rating >= 3.5 found, using all available products");
            candidates = productRepository.findAll().stream()
                    .filter(p -> p.getStockQuantity() > 0)
                    .limit(50)
                    .collect(Collectors.toList());
        }

        if (candidates.isEmpty()) {
            log.warn("No products available in database");
            return Collections.emptyList();
        }

        // Build product info for DeepSeek
        StringBuilder productInfo = new StringBuilder();
        for (Product p : candidates) {
            productInfo.append(String.format("ID:%d|%s|Rating:%.1f|Reviews:%d|CreatedAt:%s\n",
                    p.getId(),
                    p.getName(),
                    p.getAverageRating() != null ? p.getAverageRating().doubleValue() : 0.0,
                    p.getReviewCount() != null ? p.getReviewCount() : 0,
                    p.getCreatedAt() != null ? p.getCreatedAt().toString() : "N/A"
            ));
        }

        String prompt = String.format(
            "Bạn là AI dự đoán xu hướng thời trang.\n\n" +
            "Danh sách sản phẩm ứng viên:\n%s\n" +
            "Nhiệm vụ: Dự đoán %d sản phẩm SẮP TRENDING dựa trên:\n" +
            "1. Rating cao (>= 4.0 tốt hơn)\n" +
            "2. Số lượng reviews nhiều\n" +
            "3. Sản phẩm mới (gần đây hơn)\n" +
            "4. Phù hợp với xu hướng thời trang hiện tại\n\n" +
            "Trả về DANH SÁCH ID sản phẩm trending, phân tách bằng dấu phẩy.\n" +
            "CHỈ TRẢ VỀ CÁC SỐ ID, KHÔNG CẦN GIẢI THÍCH.",
            productInfo.toString(),
            limit
        );

        String aiResponse = callAI(prompt);
        List<Long> trendingIds = parseProductIds(aiResponse);

        if (!trendingIds.isEmpty()) {
            List<Product> trending = productRepository.findAllById(trendingIds).stream()
                    .filter(p -> p.getStockQuantity() > 0)
                    .limit(limit)
                    .collect(Collectors.toList());

            if (!trending.isEmpty()) {
                return trending;
            }
        }

        // Fallback: sort by rating
        log.warn("AI trending prediction parsing failed, using fallback");
        return candidates.stream()
                .sorted((p1, p2) -> {
                    BigDecimal rating1 = p2.getAverageRating() != null ? p2.getAverageRating() : BigDecimal.ZERO;
                    BigDecimal rating2 = p1.getAverageRating() != null ? p1.getAverageRating() : BigDecimal.ZERO;
                    return rating1.compareTo(rating2);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "ai-outfit", key = "#productId", cacheManager = "caffeineCacheManager")
    public Map<String, Object> generateOutfitRecommendations(Long productId) {
        log.info("AI-powered outfit recommendations for product: {} (CACHE MISS - calling DeepSeek)", productId);

        Map<String, Object> result = new HashMap<>();
        Optional<Product> productOpt = productRepository.findById(productId);

        if (productOpt.isEmpty()) {
            result.put("error", "Product not found");
            return result;
        }

        Product product = productOpt.get();
        Set<String> mainCategories = product.getCategories().stream()
            .map(Category::getName)
            .collect(Collectors.toSet());

        // Get product gender for filtering
        String productGender = product.getGender();

        // Get all available products for AI to choose from (SAME GENDER only)
        List<Product> allProducts = productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0 && !p.getId().equals(productId))
                .filter(p -> {
                    // Only match same gender or unisex products
                    if (productGender == null) return true;
                    if (p.getGender() == null) return true;
                    return p.getGender().equalsIgnoreCase(productGender) ||
                           p.getGender().equalsIgnoreCase("UNISEX") ||
                           productGender.equalsIgnoreCase("UNISEX");
                })
                .limit(100)
                .collect(Collectors.toList());

        // Build product info for AI
        StringBuilder productInfo = new StringBuilder();
        for (Product p : allProducts) {
            productInfo.append(String.format("ID:%d|%s|%s|%s VNĐ|Danh mục:%s\n",
                    p.getId(),
                    p.getName(),
                    p.getBrand() != null ? p.getBrand() : "N/A",
                    formatPrice(p.getPrice()),
                    p.getCategories().stream().map(Category::getName).collect(Collectors.joining(","))
            ));
        }

        String prompt = String.format(
            "Bạn là chuyên gia tạo OUTFIT HOÀN CHỈNH cho thời trang.\n\n" +
            "Sản phẩm chính (đã có): %s - Danh mục: %s - Giới tính: %s - Giá: %s VNĐ\n\n" +
            "Danh sách sản phẩm có sẵn (đã lọc CÙNG GIỚI TÍNH):\n%s\n" +
            "YÊU CẦU: Tìm 3-5 sản phẩm BỔ SUNG để tạo outfit hoàn chỉnh.\n\n" +
            "BẮT BUỘC:\n" +
            "1. PHẢI TÌM SẢN PHẨM KHÁC LOẠI để hoàn thiện outfit:\n" +
            "   - Nếu sản phẩm chính là ÁO → tìm QUẦN + GIÀY + PHỤ KIỆN\n" +
            "   - Nếu sản phẩm chính là QUẦN → tìm ÁO + GIÀY + PHỤ KIỆN\n" +
            "   - Nếu sản phẩm chính là GIÀY → tìm ÁO + QUẦN\n" +
            "2. Phải ĐỒNG BỘ phong cách: formal đi với formal, casual đi với casual\n" +
            "3. Phải PHÙ HỢP mức giá: không recommend giày 5 triệu với áo 200k\n" +
            "4. PHẢI là sản phẩm CÓ TRONG danh sách trên (đã lọc cùng giới tính)\n" +
            "5. TUYỆT ĐỐI KHÔNG mix Nam với Nữ (danh sách đã lọc sẵn)\n\n" +
            "CHỈ TRẢ VỀ DANH SÁCH ID, phân tách bằng dấu phẩy. KHÔNG GIẢI THÍCH.\n" +
            "VÍ DỤ: Áo khoác 599k → tìm quần jeans (300k), giày sneaker (400k)",
            product.getName(),
            String.join(", ", mainCategories),
            productGender != null ? productGender : "N/A",
            formatPrice(product.getPrice()),
            productInfo.toString()
        );

        // Get specific categories of main product (e.g., "Áo Nam", "Quần Nam")
        // to filter out same-category products
        Set<String> specificCategories = product.getCategories().stream()
            .map(Category::getName)
            .filter(name -> name.contains("Áo") || name.contains("Quần") ||
                            name.contains("Váy") || name.contains("Giày") ||
                            name.contains("Phụ kiện"))
            .collect(Collectors.toSet());

        String aiResponse = callAI(prompt);
        List<Long> complementaryIds = parseProductIds(aiResponse);
        log.info("AI returned {} product IDs for outfit: {}", complementaryIds.size(), complementaryIds);

        List<Product> complementary = Collections.emptyList();
        if (!complementaryIds.isEmpty()) {
            complementary = productRepository.findAllById(complementaryIds).stream()
                    .filter(p -> {
                        // CRITICAL: Filter by stock
                        if (p.getStockQuantity() <= 0) {
                            log.warn("Filtering out product {} (out of stock)", p.getId());
                            return false;
                        }

                        // CRITICAL: Filter by gender - STRICT CHECK to prevent mixing Nam/Nữ
                        if (productGender != null && p.getGender() != null) {
                            boolean genderMatch = p.getGender().equalsIgnoreCase(productGender) ||
                                                 p.getGender().equalsIgnoreCase("UNISEX") ||
                                                 productGender.equalsIgnoreCase("UNISEX");
                            if (!genderMatch) {
                                log.warn("Filtering out product {} due to gender mismatch: main={}, complementary={}",
                                        p.getId(), productGender, p.getGender());
                                return false;
                            }
                        }

                        // Filter out products with overlapping specific categories
                        Set<String> pSpecificCategories = p.getCategories().stream()
                            .map(Category::getName)
                            .filter(name -> name.contains("Áo") || name.contains("Quần") ||
                                            name.contains("Váy") || name.contains("Giày") ||
                                            name.contains("Phụ kiện"))
                            .collect(Collectors.toSet());

                        // Return true if NO overlap: Áo should NOT match Áo, but CAN match Quần
                        boolean categoryMatch = pSpecificCategories.stream().noneMatch(specificCategories::contains);
                        if (!categoryMatch) {
                            log.debug("Filtering out product {} due to category overlap", p.getId());
                        }
                        return categoryMatch;
                    })
                    .limit(5)
                    .collect(Collectors.toList());
        }

        // If AI failed OR returned invalid results, fallback to different categories
        if (complementary.isEmpty()) {
            log.warn("AI outfit recommendation failed or returned invalid results, using fallback");

            complementary = allProducts.stream()
                    .filter(p -> {
                        // Filter by gender (already done in allProducts, but double-check)
                        if (productGender != null && p.getGender() != null) {
                            if (!p.getGender().equalsIgnoreCase(productGender) &&
                                !p.getGender().equalsIgnoreCase("UNISEX") &&
                                !productGender.equalsIgnoreCase("UNISEX")) {
                                return false;
                            }
                        }

                        Set<String> pSpecificCategories = p.getCategories().stream()
                            .map(Category::getName)
                            .filter(name -> name.contains("Áo") || name.contains("Quần") ||
                                            name.contains("Váy") || name.contains("Giày") ||
                                            name.contains("Phụ kiện"))
                            .collect(Collectors.toSet());

                        // Return true if NO overlap in SPECIFIC categories
                        return pSpecificCategories.stream().noneMatch(specificCategories::contains);
                    })
                    .limit(3)
                    .collect(Collectors.toList());
        }

        // Generate styling tip using DeepSeek AI
        String stylingTip = generateStylingTips(productId);

        result.put("mainProduct", product);
        result.put("complementaryProducts", complementary);
        result.put("stylingTip", stylingTip);
        return result;
    }

    @Override
    public Map<String, Object> analyzeReviewsSentiment(Long productId) {
        log.info("AI-powered sentiment analysis for product: {}", productId);

        Map<String, Object> result = new HashMap<>();
        Optional<Product> productOpt = productRepository.findById(productId);

        if (productOpt.isEmpty()) {
            result.put("error", "Product not found");
            return result;
        }

        Product product = productOpt.get();

        // Use DeepSeek AI to analyze sentiment
        String prompt = String.format(
            "Bạn là AI phân tích cảm xúc từ reviews.\n\n" +
            "Sản phẩm: %s\n" +
            "Rating trung bình: %.1f/5\n" +
            "Tổng số reviews: %d\n\n" +
            "Nhiệm vụ: Phân tích cảm xúc tổng thể về sản phẩm này.\n" +
            "Trả về JSON format:\n" +
            "{\n" +
            "  \"sentiment\": \"Positive/Neutral/Negative\",\n" +
            "  \"summary\": \"Tóm tắt ngắn gọn (1-2 câu)\",\n" +
            "  \"positivePoints\": [\"điểm 1\", \"điểm 2\"],\n" +
            "  \"negativePoints\": [\"điểm 1\", \"điểm 2\"]\n" +
            "}",
            product.getName(),
            product.getAverageRating() != null ? product.getAverageRating().doubleValue() : 0.0,
            product.getReviewCount() != null ? product.getReviewCount() : 0
        );

        String aiResponse = callAI(prompt);

        // Try to parse JSON response
        try {
            JsonNode analysis = objectMapper.readTree(aiResponse);
            result.put("productId", productId);
            result.put("averageRating", product.getAverageRating() != null ? product.getAverageRating().doubleValue() : 0.0);
            result.put("totalReviews", product.getReviewCount() != null ? product.getReviewCount() : 0);
            result.put("sentiment", analysis.path("sentiment").asText("Neutral"));
            result.put("summary", analysis.path("summary").asText(""));
            result.put("positivePoints", analysis.path("positivePoints"));
            result.put("negativePoints", analysis.path("negativePoints"));
        } catch (Exception e) {
            log.error("Error parsing sentiment analysis JSON", e);
            // Fallback to simple sentiment
            result.put("productId", productId);
            result.put("averageRating", product.getAverageRating() != null ? product.getAverageRating().doubleValue() : 0.0);
            result.put("totalReviews", product.getReviewCount() != null ? product.getReviewCount() : 0);
            result.put("sentiment", product.getAverageRating() != null && product.getAverageRating().compareTo(new BigDecimal("4.0")) >= 0 ? "Positive" : "Neutral");
            result.put("aiAnalysis", aiResponse);
        }

        return result;
    }

    @Override
    public String getSizeRecommendation(Long userId, Long productId, Integer height, Integer weight) {
        log.info("AI-powered size recommendation for user {} and product {} (height: {}cm, weight: {}kg)",
                userId, productId, height, weight);

        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Product> productOpt = productRepository.findById(productId);

        if (userOpt.isEmpty() || productOpt.isEmpty()) {
            return "Không tìm thấy thông tin người dùng hoặc sản phẩm.";
        }

        User user = userOpt.get();
        Product product = productOpt.get();

        // Calculate BMI for better size recommendation
        double heightInMeters = height / 100.0;
        double bmi = weight / (heightInMeters * heightInMeters);
        String bodyType;
        if (bmi < 18.5) bodyType = "gầy";
        else if (bmi < 23) bodyType = "cân đối";
        else if (bmi < 25) bodyType = "hơi đầy đặn";
        else bodyType = "đầy đặn";

        String prompt = String.format(
            "Bạn là chuyên gia tư vấn size quần áo chuyên nghiệp.\n\n" +
            "THÔNG TIN SẢN PHẨM:\n" +
            "- Tên: %s\n" +
            "- Danh mục: %s\n" +
            "- Chất liệu: %s\n" +
            "- Size có sẵn: %s\n\n" +
            "THÔNG TIN NGƯỜI DÙNG:\n" +
            "- Họ tên: %s %s\n" +
            "- Chiều cao: %d cm\n" +
            "- Cân nặng: %d kg\n" +
            "- BMI: %.1f (Dáng người: %s)\n" +
            "- Giới tính: %s\n\n" +
            "NHIỆM VỤ:\n" +
            "Đưa ra TƯ VẤN SIZE CHI TIẾT và CHÍNH XÁC dựa trên:\n" +
            "1. Chiều cao và cân nặng thực tế của người dùng\n" +
            "2. Loại sản phẩm (áo/quần/váy) và chất liệu\n" +
            "3. Bảng size chuẩn quốc tế và Việt Nam\n" +
            "4. Dáng người (BMI) để tư vấn fit hay loose\n\n" +
            "YÊU CẦU TRẢ LỜI:\n" +
            "- Size KHUYẾN NGHỊ (trong danh sách size có sẵn)\n" +
            "- LÝ DO chi tiết (dựa vào chiều cao, cân nặng, dáng người)\n" +
            "- LƯU Ý khi mặc (áo nên chọn fit hay loose, quần nên chú ý vòng eo/mông)\n" +
            "- So sánh với size khác nếu cân nhắc (ví dụ: M hơi vừa, L thoải mái hơn)\n\n" +
            "Viết bằng tiếng Việt, tư vấn CHUYÊN NGHIỆP như nhân viên bán hàng giàu kinh nghiệm.",
            product.getName(),
            product.getCategories().stream().map(Category::getName).collect(Collectors.joining(", ")),
            product.getMaterial() != null ? product.getMaterial() : "Chưa có thông tin",
            product.getAvailableSizes() != null ? String.join(", ", product.getAvailableSizes()) : "Chưa có thông tin",
            user.getFirstName(),
            user.getLastName(),
            height,
            weight,
            bmi,
            bodyType,
            product.getGender() != null ? product.getGender() : "Unisex"
        );

        return callAI(prompt);
    }

    @Override
    public String generateStylingTips(Long productId) {
        log.info("AI-powered styling tips for product: {}", productId);

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return "Không tìm thấy sản phẩm.";
        }

        Product product = productOpt.get();
        String prompt = String.format(
            "Bạn là chuyên gia tư vấn thời trang.\n\n" +
            "Sản phẩm: %s\n" +
            "Danh mục: %s\n" +
            "Giá: %s VNĐ\n\n" +
            "Hãy đưa ra 3-4 gợi ý phối đồ THỰC TẾ, DỄ ÁP DỤNG với sản phẩm này.\n" +
            "Mỗi gợi ý gồm:\n" +
            "- Cách phối (áo + quần/váy + phụ kiện)\n" +
            "- Phù hợp với hoàn cảnh nào (đi làm/đi chơi/dạo phố)\n" +
            "Viết bằng tiếng Việt, ngắn gọn, dễ hiểu.",
            product.getName(),
            product.getCategories().stream()
                    .map(Category::getName)
                    .collect(Collectors.joining(", ")),
            formatPrice(product.getPrice())
        );

        return callAI(prompt);
    }

    @Override
    public Map<String, Object> getAllProductClusters() {
        log.info("Generating product clustering analysis using DeepSeek AI");

        // Step 1: Collect all active products with metrics
        List<Product> allProducts = productRepository.findAll().stream()
                .filter(Product::getActive)
                .collect(Collectors.toList());

        if (allProducts.isEmpty()) {
            return Map.of("error", "Không có sản phẩm nào trong hệ thống");
        }

        // Step 2: Build product metrics array
        List<Map<String, Object>> productsData = new ArrayList<>();

        for (Product product : allProducts) {
            Long salesVolume = productRepository.calculateProductSalesVolume(product);
            BigDecimal revenue = productRepository.calculateProductRevenue(product);
            Integer reviewCount = product.getReviewCount() != null ? product.getReviewCount() : 0;
            BigDecimal avgRating = product.getAverageRating() != null ? product.getAverageRating() : BigDecimal.ZERO;

            Map<String, Object> productMetrics = new HashMap<>();
            productMetrics.put("productId", product.getId());
            productMetrics.put("name", product.getName());
            productMetrics.put("price", product.getPrice().doubleValue());
            productMetrics.put("salesVolume", salesVolume);
            productMetrics.put("revenue", revenue != null ? revenue.doubleValue() : 0.0);
            productMetrics.put("reviewCount", reviewCount);
            productMetrics.put("averageRating", avgRating.doubleValue());
            productMetrics.put("category", !product.getCategories().isEmpty() ?
                product.getCategories().iterator().next().getName() : "Uncategorized");

            productsData.add(productMetrics);
        }

        // Convert to JSON string
        String jsonData;
        try {
            jsonData = new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(productsData);
        } catch (Exception e) {
            log.error("Failed to serialize product data", e);
            return Map.of("error", "Không thể xử lý dữ liệu sản phẩm");
        }

        // Step 3: Create DeepSeek prompt for analysis
        String prompt = String.format("""
            Bạn là chuyên gia phân tích dữ liệu e-commerce. Phân tích dữ liệu sản phẩm dưới đây và đưa ra insights:

            DỮ LIỆU:
            ```json
            %s
            ```

            YÊU CẦU PHÂN TÍCH:
            1. Phân cụm sản phẩm thành 3 nhóm dựa trên hiệu suất:
               - Best Sellers: salesVolume > 50 OR revenue > 5,000,000 VND
               - Moderate Performers: 10 < salesVolume <= 50 OR 1,000,000 < revenue <= 5,000,000 VND
               - Low Performers: salesVolume <= 10 OR revenue <= 1,000,000 VND

            2. Đưa ra insights cho từng nhóm:
               - Số lượng sản phẩm trong mỗi nhóm
               - Đặc điểm chung (giá trung bình, rating, category phổ biến)
               - Đề xuất chiến lược (marketing, inventory, pricing)

            3. Format output dạng text ngắn gọn, dễ đọc (không quá 300 từ).

            Chỉ trả về text phân tích, không JSON, không markdown code block.
            """, jsonData);

        // Step 4: Call DeepSeek API for analysis
        String analysis = "";
        try {
            analysis = callAI(prompt);
            log.info("DeepSeek product analysis generated successfully");
        } catch (Exception e) {
            log.error("Failed to generate product analysis with DeepSeek", e);
            analysis = "Không thể tạo phân tích tự động. Vui lòng xem dữ liệu thô.";
        }

        // Step 5: Return products data + analysis
        return Map.of(
            "success", true,
            "totalProducts", allProducts.size(),
            "products", productsData,
            "analysis", analysis,
            "clusteringRules", Map.of(
                "bestSellers", "salesVolume > 50 OR revenue > 5,000,000 VND",
                "moderatePerformers", "10 < salesVolume <= 50 OR 1,000,000 < revenue <= 5,000,000 VND",
                "lowPerformers", "salesVolume <= 10 OR revenue <= 1,000,000 VND"
            ),
            "timestamp", new java.util.Date().toString()
        );
    }

    @Override
    public Map<String, Object> getAllUserClusters() {
        log.info("Generating user clustering visualization using DeepSeek AI");

        // Step 1: Collect all users and their metrics
        List<User> allUsers = userRepository.findAll();
        if (allUsers.isEmpty()) {
            return Map.of("error", "Không có người dùng nào trong hệ thống");
        }

        // Step 2: Build JSON data array for DeepSeek
        List<Map<String, Object>> usersData = new ArrayList<>();

        for (User user : allUsers) {
            long orderCount = orderRepository.countByUser(user);
            BigDecimal totalSpent = orderRepository.sumTotalByUser(user);
            long reviewCount = reviewRepository.countByUser(user);

            Map<String, Object> userMetrics = new HashMap<>();
            userMetrics.put("userId", user.getId());
            userMetrics.put("username", user.getUsername());
            userMetrics.put("orders", orderCount);
            userMetrics.put("spending", totalSpent != null ? totalSpent.doubleValue() : 0.0);
            userMetrics.put("reviews", reviewCount);

            usersData.add(userMetrics);
        }

        // Convert to JSON string
        String jsonData;
        try {
            jsonData = new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(usersData);
        } catch (Exception e) {
            log.error("Failed to serialize user data", e);
            return Map.of("error", "Không thể xử lý dữ liệu người dùng");
        }

        // Step 3: Create DeepSeek prompt for analysis (text only, not HTML)
        String prompt = String.format("""
            Bạn là chuyên gia phân tích dữ liệu e-commerce. Phân tích dữ liệu người dùng dưới đây và đưa ra insights:

            DỮ LIỆU:
            ```json
            %s
            ```

            YÊU CẦU PHÂN TÍCH:
            1. Phân cụm người dùng thành 3 nhóm dựa trên spending (chi tiêu):
               - Low Value: spending < 200,000 VND
               - Medium Value: 200,000 <= spending < 500,000 VND
               - High Value: spending >= 500,000 VND

            2. Đưa ra insights cho từng nhóm:
               - Số lượng users trong mỗi nhóm
               - Đặc điểm hành vi (số orders, spending trung bình, số reviews)
               - Đề xuất chiến lược marketing cho từng nhóm

            3. Format output dạng text ngắn gọn, dễ đọc (không quá 300 từ).

            Chỉ trả về text phân tích, không JSON, không markdown code block.
            """, jsonData);

        // Step 4: Call DeepSeek API for analysis
        String analysis = "";
        try {
            analysis = callAI(prompt);
            log.info("DeepSeek analysis generated successfully");
        } catch (Exception e) {
            log.error("Failed to generate analysis with DeepSeek", e);
            analysis = "Không thể tạo phân tích tự động. Vui lòng xem dữ liệu thô.";
        }

        // Step 5: Return rawData + analysis
        return Map.of(
            "success", true,
            "totalUsers", allUsers.size(),
            "users", usersData,
            "analysis", analysis,
            "clusteringRules", Map.of(
                "lowValue", "spending < 200,000 VND",
                "mediumValue", "200,000 <= spending < 500,000 VND",
                "highValue", "spending >= 500,000 VND"
            ),
            "timestamp", new java.util.Date().toString()
        );
    }



    // ==================== NEW METHODS WITH EXPLANATION ====================

    @Override
    @Cacheable(value = "ai-similar", key = "#productId + '-' + #limit", cacheManager = "caffeineCacheManager")
    public AISimilarProductsResponse getSimilarProductsWithExplanation(Long productId, int limit) {
        log.info("Finding similar products with explanation for: {} (CACHE MISS - calling DeepSeek)", productId);

        Product original = productRepository.findById(productId).orElse(null);
        if (original == null) {
            return new AISimilarProductsResponse(null, Collections.emptyList(), "Không tìm thấy sản phẩm.", "", 0);
        }

        List<Product> similar = getSimilarProducts(productId, limit);

        // Build detailed context for AI
        StringBuilder context = new StringBuilder();
        context.append(String.format("Sản phẩm gốc:\n- Tên: %s\n- Thương hiệu: %s\n- Giá: %s VNĐ\n- Danh mục: %s\n\n",
            original.getName(),
            original.getBrand() != null ? original.getBrand() : "N/A",
            formatPrice(original.getPrice()),
            original.getCategories().stream().map(Category::getName).collect(Collectors.joining(", "))
        ));

        context.append("Các sản phẩm tương tự được tìm thấy:\n");
        for (int i = 0; i < similar.size(); i++) {
            Product p = similar.get(i);
            context.append(String.format("%d. %s - %s - %s VNĐ - Danh mục: %s\n",
                i + 1,
                p.getName(),
                p.getBrand() != null ? p.getBrand() : "N/A",
                formatPrice(p.getPrice()),
                p.getCategories().stream().map(Category::getName).collect(Collectors.joining(", "))
            ));
        }

        String prompt = String.format(
            "Bạn là chuyên gia phân tích sản phẩm thời trang.\n\n%s\n" +
            "YÊU CẦU: Phân tích và GIẢI THÍCH cụ thể tại sao các sản phẩm trên TƯƠNG TỰ với sản phẩm gốc.\n\n" +
            "BẮT BUỘC phải đề cập:\n" +
            "1. Điểm tương đồng về DANH MỤC (VD: \"Cả hai đều là áo nam\")\n" +
            "2. So sánh THƯƠNG HIỆU (VD: \"Khác thương hiệu: OutdoorPro vs BasicStyle\")\n" +
            "3. So sánh MỨC GIÁ (VD: \"Áo khoác 599k cao cấp hơn áo thun 159k\")\n\n" +
            "Format trả lời: 2-3 câu ngắn gọn, CỤ THỂ, bằng tiếng Việt.\n" +
            "VÍ DỤ: \"Cả hai sản phẩm đều thuộc danh mục Áo Nam trong Thời Trang Nam. Tuy nhiên, áo khoác OutdoorPro (599k) cao cấp hơn nhiều so với áo thun BasicStyle (159k). Điểm tương đồng chính là cùng phục vụ nam giới.\"",
            context.toString()
        );

        String explanation = callAI(prompt);

        return new AISimilarProductsResponse(original, similar, explanation, prompt, similar.size());
    }

    @Override
    public AIRecommendationResponse predictTrendingProductsWithExplanation(int limit) {
        log.info("Predicting trending products with explanation");

        List<Product> trending = predictTrendingProducts(limit);

        StringBuilder context = new StringBuilder();
        context.append(String.format("Danh sách %d sản phẩm được dự đoán TRENDING:\n\n", trending.size()));

        for (int i = 0; i < trending.size(); i++) {
            Product p = trending.get(i);
            context.append(String.format("%d. %s\n   - Rating: %.1f/5 (%d reviews)\n   - Giá: %s VNĐ\n   - Brand: %s\n",
                i + 1,
                p.getName(),
                p.getAverageRating() != null ? p.getAverageRating().doubleValue() : 0.0,
                p.getReviewCount() != null ? p.getReviewCount() : 0,
                formatPrice(p.getPrice()),
                p.getBrand() != null ? p.getBrand() : "N/A"
            ));
        }

        String prompt = String.format(
            "Bạn là chuyên gia dự đoán xu hướng thời trang.\n\n%s\n" +
            "YÊU CẦU: GIẢI THÍCH cụ thể tại sao dự đoán các sản phẩm này sẽ TRENDING.\n\n" +
            "BẮT BUỘC phân tích:\n" +
            "1. RATING: Có cao không? (>= 4.0 sao là tốt)\n" +
            "2. REVIEWS: Có nhiều người quan tâm không?\n" +
            "3. GIÁ: Có hợp lý với xu hướng tiêu dùng không?\n\n" +
            "Format: 2-3 câu CỤ THỂ bằng tiếng Việt, nêu SỐ LIỆU.\n" +
            "VÍ DỤ: \"Các sản phẩm này có rating trung bình trên 4.0 sao với nhiều reviews, cho thấy được khách hàng yêu thích. Mức giá từ 159k-599k phù hợp với nhiều phân khúc khách hàng. Đây là những sản phẩm có tiềm năng trending cao.\"",
            context.toString()
        );

        String explanation = callAI(prompt);

        AIRecommendationResponse response = new AIRecommendationResponse();
        response.setProducts(trending);
        response.setAiExplanation(explanation);
        response.setPrompt(prompt);
        response.setTotalRecommended(trending.size());
        return response;
    }

    @Override
    public AIAnalysisResponse analyzeReviewsSentimentWithExplanation(Long productId) {
        log.info("Analyzing sentiment with explanation for product: {}", productId);

        Map<String, Object> data = analyzeReviewsSentiment(productId);

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return new AIAnalysisResponse(data, "Không tìm thấy sản phẩm.", "");
        }

        Product product = productOpt.get();

        String prompt = String.format(
            "Bạn là chuyên gia phân tích cảm xúc từ reviews.\n\n" +
            "Sản phẩm: %s\n" +
            "Rating trung bình: %.1f/5\n" +
            "Tổng số reviews: %d\n" +
            "Sentiment hiện tại: %s\n\n" +
            "YÊU CẦU: GIẢI THÍCH CỤ THỂ kết quả phân tích sentiment dựa trên SỐ LIỆU thực tế.\n\n" +
            "BẮT BUỘC phải đề cập:\n" +
            "1. ĐÁNH GIÁ RATING: Nêu rõ rating %.1f/5 có nghĩa gì? (VD: \"Rating 4.5/5 rất tốt\" hoặc \"Rating 2.0/5 thấp\")\n" +
            "2. SỐ LƯỢNG REVIEWS: %d reviews có đáng tin không? (VD: \"Chỉ có 2 reviews, chưa đủ tin cậy\" hoặc \"Có 150 reviews, đánh giá đáng tin\")\n" +
            "3. KẾT LUẬN: Khách hàng hài lòng hay KHÔNG? Nên mua không?\n\n" +
            "Format: 2-3 câu CỤ THỂ bằng tiếng Việt, phải NÊU SỐ.\n" +
            "VÍ DỤ: \"Sản phẩm có rating 4.2/5 với 85 reviews cho thấy đa số khách hàng hài lòng. Sentiment hiện tại là Positive. Đây là sản phẩm đáng tin cậy để mua.\"",
            product.getName(),
            product.getAverageRating() != null ? product.getAverageRating().doubleValue() : 0.0,
            product.getReviewCount() != null ? product.getReviewCount() : 0,
            data.get("sentiment") != null ? data.get("sentiment").toString() : "Unknown",
            product.getAverageRating() != null ? product.getAverageRating().doubleValue() : 0.0,
            product.getReviewCount() != null ? product.getReviewCount() : 0
        );

        String explanation = callAI(prompt);

        return new AIAnalysisResponse(data, explanation, prompt);
    }

    /**
     * Analyze revenue performance using AI
     * Provides comprehensive insights about revenue trends, patterns and recommendations
     */
    @Override
    public AIAnalysisResponse analyzeRevenuePerformance(String period) {
        try {
            log.info("AI analyzing revenue performance for period: {}", period);

            // Calculate date range
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startDate = switch (period) {
                case "today" -> now.withHour(0).withMinute(0).withSecond(0);
                case "7days" -> now.minusDays(7);
                case "30days" -> now.minusDays(30);
                case "90days" -> now.minusDays(90);
                case "year" -> now.minusYears(1);
                default -> now.minusDays(30);
            };

            // Get confirmed revenue orders (PAID and DELIVERED only)
            List<com.ut.edu.backend.enums.OrderStatus> revenueStatuses = List.of(
                    com.ut.edu.backend.enums.OrderStatus.DELIVERED,
                    com.ut.edu.backend.enums.OrderStatus.PAID
            );
            List<com.ut.edu.backend.model.Order> orders = orderRepository.findByCreatedAtAfterAndStatusIn(
                    startDate, revenueStatuses
            );

            // Calculate revenue metrics
            BigDecimal totalRevenue = orders.stream()
                    .map(com.ut.edu.backend.model.Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int totalOrders = orders.size();

            BigDecimal averageOrderValue = totalOrders > 0
                    ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Group by date to see trends
            Map<String, BigDecimal> revenueByDate = orders.stream()
                    .collect(Collectors.groupingBy(
                            order -> order.getCreatedAt().toLocalDate().toString(),
                            Collectors.reducing(
                                    BigDecimal.ZERO,
                                    com.ut.edu.backend.model.Order::getTotal,
                                    BigDecimal::add
                            )
                    ));

            // Find peak and lowest revenue days
            String peakDay = "";
            BigDecimal peakRevenue = BigDecimal.ZERO;
            String lowestDay = "";
            BigDecimal lowestRevenue = totalRevenue;

            for (Map.Entry<String, BigDecimal> entry : revenueByDate.entrySet()) {
                if (entry.getValue().compareTo(peakRevenue) > 0) {
                    peakRevenue = entry.getValue();
                    peakDay = entry.getKey();
                }
                if (entry.getValue().compareTo(lowestRevenue) < 0 && entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                    lowestRevenue = entry.getValue();
                    lowestDay = entry.getKey();
                }
            }

            // Top selling products in this period
            Map<String, Integer> topProducts = new HashMap<>();
            orders.forEach(order -> {
                order.getItems().forEach(item -> {
                    String productName = item.getProduct().getName();
                    topProducts.merge(productName, item.getQuantity(), Integer::sum);
                });
            });

            List<String> top5Products = topProducts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            // Calculate growth rate (compare first half vs second half of period)
            long periodDays = ChronoUnit.DAYS.between(startDate, now);
            LocalDateTime midPoint = startDate.plusDays(periodDays / 2);

            BigDecimal firstHalfRevenue = orders.stream()
                    .filter(o -> o.getCreatedAt().isBefore(midPoint))
                    .map(com.ut.edu.backend.model.Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal secondHalfRevenue = orders.stream()
                    .filter(o -> !o.getCreatedAt().isBefore(midPoint))
                    .map(com.ut.edu.backend.model.Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            double growthRate = 0.0;
            if (firstHalfRevenue.compareTo(BigDecimal.ZERO) > 0) {
                growthRate = secondHalfRevenue.subtract(firstHalfRevenue)
                        .divide(firstHalfRevenue, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            // Build data object
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("period", period);
            data.put("periodDays", periodDays);
            data.put("totalRevenue", totalRevenue);
            data.put("totalOrders", totalOrders);
            data.put("averageOrderValue", averageOrderValue);
            data.put("peakDay", peakDay);
            data.put("peakRevenue", peakRevenue);
            data.put("lowestDay", lowestDay);
            data.put("lowestRevenue", lowestRevenue);
            data.put("growthRate", growthRate);
            data.put("firstHalfRevenue", firstHalfRevenue);
            data.put("secondHalfRevenue", secondHalfRevenue);
            data.put("top5Products", top5Products);
            data.put("activeDays", revenueByDate.size());

            // Create detailed prompt for DeepSeek AI
            String prompt = String.format(
                    "BẠN LÀ CHUYÊN GIA PHÂN TÍCH DOANH THU cho một cửa hàng thời trang trực tuyến.\n\n" +
                    "DỮ LIỆU DOANH THU %s:\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "📊 TỔNG QUAN:\n" +
                    "- Tổng doanh thu: %,.0f VNĐ\n" +
                    "- Tổng số đơn hàng: %d đơn\n" +
                    "- Giá trị đơn hàng trung bình: %,.0f VNĐ\n" +
                    "- Số ngày có doanh thu: %d/%d ngày\n\n" +
                    "📈 XU HƯỚNG TĂNG TRƯỞNG:\n" +
                    "- Doanh thu nửa đầu kỳ: %,.0f VNĐ\n" +
                    "- Doanh thu nửa cuối kỳ: %,.0f VNĐ\n" +
                    "- Tỷ lệ tăng trưởng: %.1f%%\n\n" +
                    "🔝 HIỆU SUẤT:\n" +
                    "- Ngày doanh thu cao nhất: %s (%,.0f VNĐ)\n" +
                    "- Ngày doanh thu thấp nhất: %s (%,.0f VNĐ)\n\n" +
                    "🏆 TOP 5 SẢN PHẨM BÁN CHẠY:\n%s\n\n" +
                    "YÊU CẦU PHÂN TÍCH:\n" +
                    "Hãy đưa ra phân tích chi tiết, chuyên nghiệp về:\n\n" +
                    "1. 📊 ĐÁNH GIÁ TỔNG QUAN (3-4 câu):\n" +
                    "   - Doanh thu hiện tại ở mức nào? (Tốt/Trung bình/Cần cải thiện)\n" +
                    "   - Số lượng đơn hàng có đủ lớn không?\n" +
                    "   - Giá trị đơn hàng trung bình như thế nào?\n\n" +
                    "2. 🔍 XU HƯỚNG & BIẾN ĐỘNG (3-4 câu):\n" +
                    "   - Tỷ lệ tăng trưởng %.1f%% cho thấy điều gì?\n" +
                    "   - Doanh thu có ổn định hay biến động mạnh?\n" +
                    "   - Giải thích nguyên nhân có thể của xu hướng này\n\n" +
                    "3. ⚠️ VẤN ĐỀ CẦN LƯU Ý (2-3 câu):\n" +
                    "   - Có dấu hiệu nào báo động?\n" +
                    "   - Rủi ro tiềm ẩn nào cần phòng tránh?\n\n" +
                    "4. 💡 KHUYẾN NGHỊ CỤ THỂ (5-6 điểm):\n" +
                    "   - Chiến lược tăng doanh thu ngắn hạn (1-2 tuần)\n" +
                    "   - Chiến lược phát triển dài hạn (1-3 tháng)\n" +
                    "   - Cách tối ưu giá trị đơn hàng\n" +
                    "   - Sản phẩm nào cần tập trung marketing\n" +
                    "   - Thời điểm nào nên chạy khuyến mãi\n\n" +
                    "5. 🎯 DỰ ĐOÁN & MỤC TIÊU (2-3 câu):\n" +
                    "   - Dự đoán xu hướng kỳ tiếp theo\n" +
                    "   - Mục tiêu doanh thu thực tế nên đặt ra\n\n" +
                    "Format: Trả lời bằng tiếng Việt, chuyên nghiệp, dễ hiểu, có số liệu cụ thể.",
                    period.equals("today") ? "HÔM NAY" :
                    period.equals("7days") ? "7 NGÀY QUA" :
                    period.equals("30days") ? "30 NGÀY QUA" :
                    period.equals("90days") ? "90 NGÀY QUA" : "1 NĂM QUA",
                    totalRevenue,
                    totalOrders,
                    averageOrderValue,
                    revenueByDate.size(),
                    periodDays,
                    firstHalfRevenue,
                    secondHalfRevenue,
                    growthRate,
                    peakDay.isEmpty() ? "N/A" : peakDay,
                    peakRevenue,
                    lowestDay.isEmpty() ? "N/A" : lowestDay,
                    lowestRevenue.compareTo(totalRevenue) == 0 ? BigDecimal.ZERO : lowestRevenue,
                    formatTopProducts(top5Products),
                    growthRate
            );

            // Call DeepSeek AI for analysis
            String aiAnalysis = callAI(prompt);

            log.info("AI revenue analysis completed for period: {}", period);

            return new AIAnalysisResponse(data, aiAnalysis, prompt);

        } catch (Exception e) {
            log.error("Failed to analyze revenue performance", e);
            throw new RuntimeException("AI revenue analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to format top products list
     */
    private String formatTopProducts(List<String> topProducts) {
        if (topProducts.isEmpty()) {
            return "   (Chưa có dữ liệu sản phẩm)";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < topProducts.size(); i++) {
            sb.append(String.format("   %d. %s\n", i + 1, topProducts.get(i)));
        }
        return sb.toString();
    }

    @Override
    public Map<String, Object> getAllOrderClusters() {
        log.info("Generating order clustering analysis using DeepSeek AI");

        // Step 1: Collect all orders with metrics
        List<com.ut.edu.backend.model.Order> allOrders = orderRepository.findAll();

        if (allOrders.isEmpty()) {
            return Map.of("error", "Không có đơn hàng nào trong hệ thống");
        }

        // Step 2: Build order metrics array
        List<Map<String, Object>> ordersData = new ArrayList<>();

        for (com.ut.edu.backend.model.Order order : allOrders) {
            int itemCount = order.getItems() != null ? order.getItems().size() : 0;

            Map<String, Object> orderMetrics = new HashMap<>();
            orderMetrics.put("orderId", order.getId());
            orderMetrics.put("orderNumber", order.getOrderNumber());
            orderMetrics.put("customerName", order.getUser() != null ? order.getUser().getUsername() : "Unknown");
            orderMetrics.put("totalAmount", order.getTotal() != null ? order.getTotal().doubleValue() : 0.0);
            orderMetrics.put("itemCount", itemCount);
            orderMetrics.put("status", order.getStatus() != null ? order.getStatus().name() : "UNKNOWN");
            orderMetrics.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");

            ordersData.add(orderMetrics);
        }

        // Convert to JSON string
        String jsonData;
        try {
            jsonData = new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(ordersData);
        } catch (Exception e) {
            log.error("Failed to serialize order data", e);
            return Map.of("error", "Không thể xử lý dữ liệu đơn hàng");
        }

        // Step 3: Create DeepSeek prompt for analysis
        String prompt = String.format("""
            Bạn là chuyên gia phân tích dữ liệu e-commerce. Phân tích dữ liệu đơn hàng dưới đây và đưa ra insights:

            DỮ LIỆU:
            ```json
            %s
            ```

            YÊU CẦU PHÂN TÍCH:
            1. Phân cụm đơn hàng thành 4 nhóm dựa trên totalAmount (giá trị đơn hàng):
               - Low Value Orders: < 200,000 VND
               - Medium Value Orders: 200,000 - 500,000 VND
               - High Value Orders: 500,000 - 1,000,000 VND
               - Premium Orders: > 1,000,000 VND

            2. Phân tích theo trạng thái đơn hàng:
               - PENDING (Đang chờ xử lý)
               - PROCESSING (Đang xử lý)
               - SHIPPED (Đã gửi hàng)
               - DELIVERED (Đã giao)
               - CANCELLED (Đã hủy)
               - REFUNDED (Đã hoàn tiền)

            3. Tính toán các chỉ số:
               - Tổng số đơn hàng trong mỗi cluster
               - Giá trị trung bình đơn hàng
               - Số lượng sản phẩm trung bình
               - Tổng doanh thu theo cluster
               - Tỷ lệ phần trăm mỗi cluster

            4. Insights kinh doanh:
               - Xu hướng mua hàng (đơn lẻ vs đơn bulk)
               - Khách hàng nào tạo giá trị cao nhất
               - Trạng thái đơn hàng nào chiếm tỷ lệ lớn
               - Đề xuất để tăng giá trị đơn hàng trung bình

            QUAN TRỌNG: Chỉ trả về TEXT thuần (không markdown, không HTML, không emoji), dạng report như sau:

            ==============================================
            PHÂN TÍCH PHÂN CỤM ĐỠN HÀNG (ORDER CLUSTERING)
            ==============================================

            📊 PHÂN CỤM THEO GIÁ TRỊ ĐƠN HÀNG:

            1. LOW VALUE ORDERS (< 200K)
               - Số đơn: X đơn (Y%%)
               - Giá trị TB: Z VND
               - Items TB: N sản phẩm
               - Tổng doanh thu: M VND

            2. MEDIUM VALUE ORDERS (200K-500K)
               ...

            3. HIGH VALUE ORDERS (500K-1M)
               ...

            4. PREMIUM ORDERS (> 1M)
               ...

            📈 PHÂN TÍCH THEO TRẠNG THÁI:

            - PENDING: X đơn (Y%%)
            - PROCESSING: ...
            - DELIVERED: ...
            - CANCELLED: ...

            💡 INSIGHTS & RECOMMENDATIONS:

            1. [Insight về xu hướng mua hàng]
            2. [Insight về khách hàng giá trị cao]
            3. [Đề xuất để tăng AOV - Average Order Value]
            4. [Đề xuất về quản lý trạng thái đơn hàng]

            ==============================================
            """, jsonData);

        // Step 4: Call DeepSeek API
        String aiAnalysis;
        try {
            aiAnalysis = callAI(prompt);
        } catch (Exception e) {
            log.error("Failed to call AI for order clustering", e);
            aiAnalysis = "⚠️ Không thể tạo phân tích AI. Vui lòng thử lại sau.";
        }

        // Step 5: Return response
        return Map.of(
            "analysis", aiAnalysis,
            "totalOrders", allOrders.size(),
            "rawData", ordersData,
            "timestamp", new java.util.Date().toString()
        );
    }


}

