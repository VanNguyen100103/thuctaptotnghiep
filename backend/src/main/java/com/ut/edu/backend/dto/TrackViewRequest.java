package com.ut.edu.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for tracking product view
 *
 * Example JSON:
 * {
 *   "productId": 13,
 *   "sessionId": "anonymous-session-abc123"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackViewRequest {

    /**
     * Product ID being viewed
     */
    @NotNull(message = "Product ID is required")
    private Long productId;

    /**
     * Session ID for anonymous users (optional - if not provided and user is not authenticated, will generate one)
     */
    private String sessionId;

    /**
     * User's IP address (optional - can be extracted from request)
     */
    private String ipAddress;

    /**
     * User agent string (optional - can be extracted from request)
     */
    private String userAgent;
}
