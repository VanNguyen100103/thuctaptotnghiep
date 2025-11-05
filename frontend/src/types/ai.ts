/**
 * AI Types
 */

export interface ProductRecommendation {
  productId: number;
  productName: string;
  productSlug: string;
  productImage: string;
  price: number;
  discountPrice?: number;
  rating?: number;
  score: number;
  reason: string;
}

export interface OutfitSuggestion {
  id: string;
  name: string;
  description: string;
  products: Array<{
    productId: number;
    productName: string;
    productSlug: string;
    productImage: string;
    price: number;
    category: string;
  }>;
  totalPrice: number;
  style: string;
  occasion: string;
}

export interface UserCluster {
  clusterId: number;
  clusterName: string;
  characteristics: string[];
  size: number;
}

export interface ProductCluster {
  clusterId: number;
  clusterName: string;
  categoryDistribution: Record<string, number>;
  priceRange: { min: number; max: number };
  averageRating: number;
  size: number;
}

export interface SimilarProduct {
  productId: number;
  productName: string;
  productSlug: string;
  productImage: string;
  price: number;
  compareAtPrice?: number;
  averageRating?: number;
  reviewCount?: number;
  rating?: number;
  similarity: number;
}

// Response wrappers from backend
export interface AISimilarProductsResponse {
  originalProduct: any; // Product type
  similarProducts: any[]; // Product[] type
  aiExplanation: string;
  prompt: string;
  totalFound: number;
}

export interface AIRecommendationResponse {
  products: any[]; // Product[] type
  aiExplanation: string;
  prompt: string;
  totalRecommended: number;
}
