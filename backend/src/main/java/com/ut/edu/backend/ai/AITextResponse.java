package com.ut.edu.backend.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for AI Text Response with prompt
 * Used for: size recommendation, styling tips, etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AITextResponse {
    private String text;
    private String prompt;
}
