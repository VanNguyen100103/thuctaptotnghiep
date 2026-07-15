package com.ut.edu.backend.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for user clustering analytics
 * Contains data for visualization (scatter plot)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserClusterResponse {

    /**
     * List of users with their cluster assignment and coordinates
     */
    private List<UserClusterPoint> users;

    /**
     * Cluster centroids (centers) for visualization
     */
    private List<ClusterCentroid> centroids;

    /**
     * Summary statistics for each cluster
     */
    private Map<Integer, ClusterSummary> clusterSummaries;

    /**
     * Feature names used for clustering (e.g., "totalSpent", "orderCount")
     */
    private List<String> featureNames;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserClusterPoint {
        private Long userId;
        private String username;
        private Integer cluster; // Cluster ID (0, 1, 2, ...)
        private Double x; // Feature 1 (e.g., totalSpent)
        private Double y; // Feature 2 (e.g., orderCount)
        private String clusterLabel; // Human-readable label (e.g., "High Value", "Low Activity")
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
        private Integer userCount;
        private Double avgTotalSpent;
        private Double avgOrderCount;
        private Double avgReviewCount;
    }
}
