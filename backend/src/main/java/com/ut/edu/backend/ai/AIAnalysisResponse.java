package com.ut.edu.backend.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for Generic AI Analysis Response with explanation
 * Used for: sentiment analysis, clusters, predictions, etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysisResponse {
    private Map<String, Object> data;
    private String aiExplanation;
    private String prompt;
}
