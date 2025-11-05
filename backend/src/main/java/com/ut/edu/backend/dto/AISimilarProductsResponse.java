package com.ut.edu.backend.dto;

import com.ut.edu.backend.model.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for AI Similar Products Response with explanation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AISimilarProductsResponse {
    private Product originalProduct;
    private List<Product> similarProducts;
    private String aiExplanation;
    private String prompt;
    private int totalFound;
}
