package com.ut.edu.backend.ai;

import com.ut.edu.backend.product.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for AI Recommendation Response with explanation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIRecommendationResponse {
    private List<Product> products;
    private String aiExplanation;
    private String prompt;
    private int totalRecommended;
}
