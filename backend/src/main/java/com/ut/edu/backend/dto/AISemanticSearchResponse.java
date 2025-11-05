package com.ut.edu.backend.dto;

import com.ut.edu.backend.model.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for AI Semantic Search Response with explanation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AISemanticSearchResponse {
    private String query;
    private List<Product> products;
    private String aiExplanation;
    private String prompt;
    private int totalFound;
}
