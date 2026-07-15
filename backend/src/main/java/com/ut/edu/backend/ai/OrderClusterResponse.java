package com.ut.edu.backend.ai;

import com.ut.edu.backend.order.Order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for order clustering analytics
 * Contains data for visualization (scatter plot)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderClusterResponse {

    /**
     * List of orders with their cluster assignment and coordinates
     */
    private List<OrderClusterPoint> orders;

    /**
     * Cluster centroids (centers) for visualization
     */
    private List<ClusterCentroid> centroids;

    /**
     * Summary statistics for each cluster
     */
    private Map<Integer, ClusterSummary> clusterSummaries;

    /**
     * Feature names used for clustering (e.g., "totalAmount", "itemCount")
     */
    private List<String> featureNames;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderClusterPoint {
        private Long orderId;
        private String orderCode;
        private String customerName;
        private Integer cluster; // Cluster ID (0, 1, 2, ...)
        private Double x; // Feature 1 (e.g., totalAmount)
        private Double y; // Feature 2 (e.g., itemCount)
        private String clusterLabel; // Human-readable label (e.g., "High Value", "Bulk Order")
        private String status; // Order status
        private BigDecimal totalAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterCentroid {
        private Integer clusterId;
        private Double x; // Center X coordinate
        private Double y; // Center Y coordinate
        private String label; // Cluster label
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterSummary {
        private Integer clusterId;
        private String label;
        private Integer orderCount;
        private Double avgTotalAmount;
        private Double avgItemCount;
        private Double totalRevenue;
    }
}
