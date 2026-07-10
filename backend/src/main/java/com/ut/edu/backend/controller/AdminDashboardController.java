package com.ut.edu.backend.controller;

import com.ut.edu.backend.enums.OrderStatus;
import com.ut.edu.backend.model.Order;

import com.ut.edu.backend.repository.OrderRepository;
import com.ut.edu.backend.repository.ProductRepository;
import com.ut.edu.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin Dashboard Controller
 * Provides statistics and analytics for admin dashboard
 */
@RestController
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminDashboardController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.ut.edu.backend.service.inter.IAIService aiService;

    /**
     * Get dashboard overview statistics
     * GET /api/admin/dashboard/overview
     */
    @GetMapping("/overview")
    public ResponseEntity<?> getDashboardOverview() {
        try {
            // User statistics - OPTIMIZED with database queries
            long totalUsers = userRepository.count();
            long activeUsers = userRepository.countByEnabled(true);

            // Product statistics - OPTIMIZED
            long totalProducts = productRepository.count();
            long activeProducts = productRepository.countByActive(true);
            long outOfStock = productRepository.countOutOfStock();

            // Order statistics - OPTIMIZED
            long totalOrders = orderRepository.count();
            long pendingOrders = orderRepository.countByStatusIn(
                    List.of(OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING));
            long processingOrders = orderRepository.countByStatus(OrderStatus.PROCESSING);
            long shippedOrders = orderRepository.countByStatus(OrderStatus.SHIPPED);

            // Revenue statistics - OPTIMIZED with single query
            List<OrderStatus> revenueStatuses = List.of(OrderStatus.DELIVERED, OrderStatus.PAID);
            BigDecimal totalRevenue = orderRepository.sumTotalByStatusIn(revenueStatuses);

            // Today's revenue - OPTIMIZED with single query
            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            BigDecimal todayRevenue = orderRepository.sumTotalByCreatedAtAfterAndStatusIn(
                    todayStart, revenueStatuses);

            Map<String, Object> overview = new HashMap<>();

            // User stats
            Map<String, Object> userStats = new HashMap<>();
            userStats.put("total", totalUsers);
            userStats.put("active", activeUsers);
            userStats.put("inactive", totalUsers - activeUsers);
            overview.put("users", userStats);

            // Product stats
            Map<String, Object> productStats = new HashMap<>();
            productStats.put("total", totalProducts);
            productStats.put("active", activeProducts);
            productStats.put("inactive", totalProducts - activeProducts);
            productStats.put("outOfStock", outOfStock);
            overview.put("products", productStats);

            // Order stats
            Map<String, Object> orderStats = new HashMap<>();
            orderStats.put("total", totalOrders);
            orderStats.put("pending", pendingOrders);
            orderStats.put("processing", processingOrders);
            orderStats.put("shipped", shippedOrders);
            overview.put("orders", orderStats);

            // Revenue stats
            Map<String, Object> revenueStats = new HashMap<>();
            revenueStats.put("total", totalRevenue);
            revenueStats.put("today", todayRevenue);
            overview.put("revenue", revenueStats);

            return ResponseEntity.ok(overview);

        } catch (Exception e) {
            log.error("Failed to get dashboard overview", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve dashboard data"));
        }
    }

    /**
     * Get sales statistics by period with pagination (OPTIMIZED for histogram chart)
     * GET /api/admin/dashboard/sales?period=7days&page=0&size=1000
     *
     * @param period Time period (today, 7days, 30days, 90days, year)
     * @param page Page number (default: 0)
     * @param size Page size (default: 1000, max: 10000 to prevent memory issues)
     */
    @GetMapping("/sales")
    public ResponseEntity<?> getSalesStatistics(
            @RequestParam(defaultValue = "7days") String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size) {
        try {
            // Prevent excessive page sizes to avoid memory issues
            if (size > 10000) {
                size = 10000;
            }

            LocalDateTime startDate = calculateStartDate(period);
            List<OrderStatus> revenueStatuses = List.of(OrderStatus.DELIVERED, OrderStatus.PAID);

            // OPTIMIZED: Use paginated database query
            Pageable pageable = PageRequest.of(page, size);
            Page<Order> orderPage = orderRepository.findByCreatedAtAfterAndStatusInPaginated(
                    startDate, revenueStatuses, pageable);

            List<Order> orders = orderPage.getContent();

            // Group by date
            Map<String, BigDecimal> salesByDate = orders.stream()
                    .collect(Collectors.groupingBy(
                            order -> order.getCreatedAt().toLocalDate().toString(),
                            Collectors.reducing(
                                    BigDecimal.ZERO,
                                    Order::getTotal,
                                    BigDecimal::add
                            )
                    ));

            // Fill missing dates with zero values for complete histogram
            Map<String, BigDecimal> completeSalesData = fillMissingDates(salesByDate, startDate, LocalDateTime.now());

            // Calculate totals from current page
            BigDecimal totalSales = orders.stream()
                    .map(Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int totalOrders = orders.size();

            BigDecimal averageOrderValue = totalOrders > 0
                    ? totalSales.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> salesStats = new HashMap<>();
            salesStats.put("period", period);
            salesStats.put("totalSales", totalSales);
            salesStats.put("totalOrders", totalOrders);
            salesStats.put("averageOrderValue", averageOrderValue);
            salesStats.put("salesByDate", completeSalesData);

            // Pagination metadata
            salesStats.put("currentPage", orderPage.getNumber());
            salesStats.put("pageSize", orderPage.getSize());
            salesStats.put("totalPages", orderPage.getTotalPages());
            salesStats.put("totalElements", orderPage.getTotalElements());
            salesStats.put("hasNext", orderPage.hasNext());
            salesStats.put("hasPrevious", orderPage.hasPrevious());

            // Revenue calculation note
            salesStats.put("includedStatuses", List.of("DELIVERED", "PAID"));
            salesStats.put("note", "Sales statistics include only DELIVERED and PAID orders (confirmed revenue)");

            return ResponseEntity.ok(salesStats);

        } catch (Exception e) {
            log.error("Failed to get sales statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve sales statistics"));
        }
    }

    /**
     * Get top selling products (OPTIMIZED)
     * GET /api/admin/dashboard/top-products?limit=10
     *
     * Counts products from PAID and DELIVERED orders (confirmed revenue only)
     */
    @GetMapping("/top-products")
    public ResponseEntity<?> getTopProducts(@RequestParam(defaultValue = "10") int limit) {
        try {
            // Get orders with confirmed revenue (PAID and DELIVERED)
            List<Order> paidOrders = orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.PAID);
            List<Order> deliveredOrders = orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.DELIVERED);

            // Combine both lists
            List<Order> confirmedOrders = new ArrayList<>();
            confirmedOrders.addAll(paidOrders);
            confirmedOrders.addAll(deliveredOrders);

            // Count product sales
            Map<Long, Long> productSales = new HashMap<>();
            Map<Long, String> productNames = new HashMap<>();
            Map<Long, BigDecimal> productRevenue = new HashMap<>();

            confirmedOrders.forEach(order -> {
                order.getItems().forEach(item -> {
                    Long productId = item.getProduct().getId();
                    productSales.merge(productId, (long) item.getQuantity(), Long::sum);
                    productNames.putIfAbsent(productId, item.getProduct().getName());
                    productRevenue.merge(productId,
                            item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())),
                            BigDecimal::add);
                });
            });

            // Sort by sales count and get top N
            List<Map<String, Object>> topProducts = productSales.entrySet().stream()
                    .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                    .limit(limit)
                    .map(entry -> {
                        Map<String, Object> productInfo = new HashMap<>();
                        productInfo.put("productId", entry.getKey());
                        productInfo.put("productName", productNames.get(entry.getKey()));
                        productInfo.put("unitsSold", entry.getValue());
                        productInfo.put("revenue", productRevenue.get(entry.getKey()));
                        return productInfo;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("topProducts", topProducts);
            response.put("limit", limit);
            response.put("totalOrdersAnalyzed", confirmedOrders.size());
            response.put("includedStatuses", List.of("PAID", "DELIVERED"));
            response.put("note", "Top products based on PAID and DELIVERED orders only (confirmed revenue)");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get top products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve top products"));
        }
    }

    /**
     * Get order statistics by status (OPTIMIZED - uses single query)
     * GET /api/admin/dashboard/order-status-stats
     *
     * Revenue calculation logic:
     * - Only DELIVERED and PAID orders count as confirmed revenue
     * - Other statuses show total order value but NOT confirmed revenue
     */
    @GetMapping("/order-status-stats")
    public ResponseEntity<?> getOrderStatusStatistics() {
        try {
            // OPTIMIZED: Use single database query with GROUP BY
            List<Object[]> results = orderRepository.getOrderStatisticsByStatus();

            // Define which statuses count as confirmed revenue
            List<OrderStatus> confirmedRevenueStatuses = List.of(OrderStatus.DELIVERED, OrderStatus.PAID);

            List<Map<String, Object>> statusStats = results.stream()
                    .map(row -> {
                        OrderStatus status = (OrderStatus) row[0];
                        Long count = (Long) row[1];
                        BigDecimal totalOrderValue = (BigDecimal) row[2];

                        Map<String, Object> stat = new HashMap<>();
                        stat.put("status", status.toString());
                        stat.put("count", count);
                        stat.put("totalOrderValue", totalOrderValue); // Total value regardless of status

                        // Only count as revenue if status is DELIVERED or PAID
                        boolean isConfirmedRevenue = confirmedRevenueStatuses.contains(status);
                        stat.put("confirmedRevenue", isConfirmedRevenue ? totalOrderValue : BigDecimal.ZERO);
                        stat.put("isConfirmedRevenue", isConfirmedRevenue);

                        return stat;
                    })
                    .collect(Collectors.toList());

            // Calculate totals
            BigDecimal totalOrderValue = statusStats.stream()
                    .map(stat -> (BigDecimal) stat.get("totalOrderValue"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalConfirmedRevenue = statusStats.stream()
                    .map(stat -> (BigDecimal) stat.get("confirmedRevenue"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> response = new HashMap<>();
            response.put("orderStatusStats", statusStats);
            response.put("totalOrderValue", totalOrderValue);
            response.put("totalConfirmedRevenue", totalConfirmedRevenue);
            response.put("note", "confirmedRevenue only includes DELIVERED and PAID orders");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get order status statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve order status statistics"));
        }
    }

    /**
     * Get order distribution by status for Pie Chart
     * GET /api/admin/dashboard/revenue-pie-chart
     *
     * Shows ALL order statuses with clear indication of which ones count as confirmed revenue
     * - DELIVERED and PAID = confirmed revenue (highlighted)
     * - Other statuses = pending/cancelled orders (not revenue)
     */
    @GetMapping("/revenue-pie-chart")
    public ResponseEntity<?> getRevenuePieChart() {
        try {
            // Get order statistics grouped by status
            List<Object[]> results = orderRepository.getOrderStatisticsByStatus();

            // Define which statuses count as confirmed revenue
            List<OrderStatus> confirmedRevenueStatuses = List.of(OrderStatus.DELIVERED, OrderStatus.PAID);

            // Build pie chart data for ALL statuses
            List<Map<String, Object>> pieChartData = results.stream()
                    .map(row -> {
                        OrderStatus status = (OrderStatus) row[0];
                        Long count = (Long) row[1];
                        BigDecimal orderValue = (BigDecimal) row[2];

                        boolean isConfirmedRevenue = confirmedRevenueStatuses.contains(status);

                        Map<String, Object> data = new HashMap<>();
                        data.put("status", status.toString());
                        data.put("count", count);
                        data.put("orderValue", orderValue);
                        data.put("isConfirmedRevenue", isConfirmedRevenue);
                        data.put("color", getColorForStatus(status));

                        return data;
                    })
                    .sorted((a, b) -> {
                        // Sort: confirmed revenue first, then by order value
                        boolean aIsRevenue = (Boolean) a.get("isConfirmedRevenue");
                        boolean bIsRevenue = (Boolean) b.get("isConfirmedRevenue");
                        if (aIsRevenue != bIsRevenue) {
                            return aIsRevenue ? -1 : 1; // Revenue statuses first
                        }
                        return ((BigDecimal) b.get("orderValue")).compareTo((BigDecimal) a.get("orderValue"));
                    })
                    .collect(Collectors.toList());

            // Calculate totals
            BigDecimal totalOrderValue = pieChartData.stream()
                    .map(data -> (BigDecimal) data.get("orderValue"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalConfirmedRevenue = pieChartData.stream()
                    .filter(data -> (Boolean) data.get("isConfirmedRevenue"))
                    .map(data -> (BigDecimal) data.get("orderValue"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long totalOrders = pieChartData.stream()
                    .mapToLong(data -> (Long) data.get("count"))
                    .sum();

            long confirmedOrders = pieChartData.stream()
                    .filter(data -> (Boolean) data.get("isConfirmedRevenue"))
                    .mapToLong(data -> (Long) data.get("count"))
                    .sum();

            // Add percentage of total orders
            pieChartData.forEach(data -> {
                Long count = (Long) data.get("count");
                double percentage = totalOrders > 0
                        ? (count.doubleValue() / totalOrders) * 100
                        : 0.0;
                data.put("percentage", Math.round(percentage * 100.0) / 100.0);
            });

            Map<String, Object> response = new HashMap<>();
            response.put("pieChartData", pieChartData);
            response.put("totalOrders", totalOrders);
            response.put("totalOrderValue", totalOrderValue);
            response.put("confirmedOrders", confirmedOrders);
            response.put("totalConfirmedRevenue", totalConfirmedRevenue);
            response.put("revenueStatuses", List.of("DELIVERED", "PAID"));
            response.put("note", "Pie chart shows all order statuses. Only DELIVERED and PAID count as confirmed revenue.");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get revenue pie chart data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve pie chart data"));
        }
    }

    /**
     * Get recent activities (orders, users) - OPTIMIZED
     * GET /api/admin/dashboard/recent-activities?limit=20
     */
    @GetMapping("/recent-activities")
    public ResponseEntity<?> getRecentActivities(@RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> activities = new ArrayList<>();

            int itemsPerType = limit / 2;

            // OPTIMIZED: Use database query with pagination instead of findAll()
            List<Order> recentOrders = orderRepository.findRecentOrders(
                    org.springframework.data.domain.PageRequest.of(0, itemsPerType));

            recentOrders.forEach(order -> {
                Map<String, Object> activity = new HashMap<>();
                activity.put("type", "ORDER");
                activity.put("timestamp", order.getCreatedAt());
                activity.put("description", "Đơn đặt hàng mới" + order.getOrderNumber());
                activity.put("status", order.getStatus());
                activity.put("amount", order.getTotal());
                activities.add(activity);
            });

            // OPTIMIZED: Use database query with pagination instead of findAll()
            List<com.ut.edu.backend.model.User> recentUsers = userRepository.findRecentUsers(
                    org.springframework.data.domain.PageRequest.of(0, itemsPerType));

            recentUsers.forEach(user -> {
                Map<String, Object> activity = new HashMap<>();
                activity.put("type", "USER");
                activity.put("timestamp", user.getCreatedAt());
                activity.put("description", "Người dùng mới đã đăng ký: " + user.getUsername());
                activity.put("email", user.getEmail());
                activities.add(activity);
            });

            // Sort all activities by timestamp
            activities.sort((a, b) ->
                    ((LocalDateTime) b.get("timestamp")).compareTo((LocalDateTime) a.get("timestamp"))
            );

            return ResponseEntity.ok(Map.of(
                    "activities", activities.stream().limit(limit).collect(Collectors.toList()),
                    "count", Math.min(activities.size(), limit)
            ));

        } catch (Exception e) {
            log.error("Failed to get recent activities", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve recent activities"));
        }
    }

    /**
     * Helper method to calculate start date based on period
     */
    private LocalDateTime calculateStartDate(String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period) {
            case "today" -> now.withHour(0).withMinute(0).withSecond(0);
            case "7days" -> now.minusDays(7);
            case "30days" -> now.minusDays(30);
            case "90days" -> now.minusDays(90);
            case "year" -> now.minusYears(1);
            default -> now.minusDays(7);
        };
    }

    /**
     * Helper method to fill missing dates with zero values for histogram
     * Ensures complete data for chart rendering
     */
    private Map<String, BigDecimal> fillMissingDates(
            Map<String, BigDecimal> salesByDate,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        Map<String, BigDecimal> completeSalesData = new java.util.LinkedHashMap<>();
        LocalDateTime currentDate = startDate.toLocalDate().atStartOfDay();
        LocalDateTime end = endDate.toLocalDate().atStartOfDay();

        while (!currentDate.isAfter(end)) {
            String dateKey = currentDate.toLocalDate().toString();
            completeSalesData.put(dateKey, salesByDate.getOrDefault(dateKey, BigDecimal.ZERO));
            currentDate = currentDate.plusDays(1);
        }

        return completeSalesData;
    }

    /**
     * Helper method to get color suggestions for order status in pie chart
     */
    private String getColorForStatus(OrderStatus status) {
        return switch (status) {
            case DELIVERED -> "#10b981"; // green
            case PAID -> "#3b82f6";      // blue
            case PROCESSING -> "#f59e0b"; // amber
            case SHIPPED -> "#8b5cf6";    // purple
            case PENDING -> "#6b7280";    // gray
            case PAYMENT_PENDING -> "#f97316"; // orange
            case CANCELLED -> "#ef4444";  // red
            case REFUNDED -> "#ec4899";   // pink
            case FAILED -> "#dc2626";     // dark red
        };
    }

    /**
     * AI Revenue Analysis - Powered by DeepSeek AI
     * GET /api/admin/dashboard/ai-revenue-analysis?period=30days
     *
     * Analyzes revenue performance using DeepSeek AI to provide:
     * - Comprehensive insights about revenue trends
     * - Growth analysis and patterns
     * - Actionable recommendations for improvement
     * - Predictions and targets
     *
     * @param period Time period (today, 7days, 30days, 90days, year) - default: 30days
     * @return AI analysis with data, insights, and recommendations
     */
    @GetMapping("/ai-revenue-analysis")
    public ResponseEntity<?> getAIRevenueAnalysis(@RequestParam(defaultValue = "30days") String period) {
        try {
            log.info("Admin requested AI revenue analysis for period: {}", period);

            // Validate period parameter
            if (!List.of("today", "7days", "30days", "90days", "year").contains(period)) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "error", "Invalid period",
                                "message", "Period must be one of: today, 7days, 30days, 90days, year",
                                "receivedPeriod", period
                        ));
            }

            // Call AI service for analysis
            com.ut.edu.backend.dto.AIAnalysisResponse analysis = aiService.analyzeRevenuePerformance(period);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("period", period);
            response.put("data", analysis.getData());
            response.put("aiAnalysis", analysis.getAiExplanation());
            response.put("generatedAt", LocalDateTime.now());
            response.put("note", "Analysis powered by DeepSeek AI");

            log.info("AI revenue analysis completed successfully for period: {}", period);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get AI revenue analysis for period: {}", period, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "AI Analysis Failed",
                            "message", "Unable to generate revenue analysis. " + e.getMessage(),
                            "period", period
                    ));
        }
    }
}
