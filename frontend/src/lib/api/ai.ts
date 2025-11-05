/**
 * AI API Client
 * Handles all AI-powered features (Gemini integration)
 */

import { get, post } from './client';
import type {
  ProductRecommendation,
  OutfitSuggestion,
  UserCluster,
  ProductCluster,
  AISimilarProductsResponse,
  AIRecommendationResponse,
} from '@/types/ai';
import type { Product } from '@/types/product';

// ==================== PUBLIC ENDPOINTS ====================

/**
 * Get personalized product recommendations
 * Endpoint: GET /api/ai/recommendations?userId={userId}&limit={limit}
 */
export async function getPersonalizedRecommendations(
  userId?: number,
  limit: number = 10
): Promise<ProductRecommendation[]> {
  const params = new URLSearchParams();
  if (userId) params.append('userId', userId.toString());
  params.append('limit', limit.toString());

  return get<ProductRecommendation[]>(`/api/ai/recommendations?${params.toString()}`);
}

/**
 * Get product recommendations based on product ID
 * Endpoint: GET /api/ai/recommendations?productId={productId}&limit={limit}
 */
export async function getProductBasedRecommendations(
  productId: number,
  limit: number = 6
): Promise<Product[]> {
  const response = await get<AIRecommendationResponse>(`/api/ai/recommendations?productId=${productId}&limit=${limit}`);
  return response.products;
}

/**
 * Get recommendations based on category
 * Endpoint: GET /ai/recommendations/category/{categoryId}?limit={limit}
 */
export async function getCategoryRecommendations(
  categoryId: number,
  limit: number = 10
): Promise<ProductRecommendation[]> {
  return get<ProductRecommendation[]>(`/ai/recommendations/category/${categoryId}?limit=${limit}`);
}

/**
 * Get outfit suggestions for a product
 * Endpoint: GET /ai/outfits/product/{productId}
 */
export async function getOutfitSuggestions(productId: number): Promise<OutfitSuggestion[]> {
  return get<OutfitSuggestion[]>(`/ai/outfits/product/${productId}`);
}

/**
 * Generate outfit for an occasion
 * Endpoint: POST /ai/outfits/generate
 */
export async function generateOutfit(data: {
  occasion: string;
  style?: string;
  budget?: number;
  gender?: 'MALE' | 'FEMALE' | 'UNISEX';
}): Promise<OutfitSuggestion[]> {
  return post<OutfitSuggestion[]>('/ai/outfits/generate', data);
}

/**
 * Get similar products using AI
 * Endpoint: GET /api/ai/similar/{productId}?limit={limit}
 */
export async function getSimilarProducts(
  productId: number,
  limit: number = 6
): Promise<AISimilarProductsResponse> {
  return get<AISimilarProductsResponse>(`/api/ai/similar/${productId}?limit=${limit}`);
}

/**
 * Get outfit recommendations for a product
 * Endpoint: GET /api/ai/outfit/{productId}
 */
export async function getOutfitRecommendations(
  productId: number
): Promise<{
  mainProduct: Product;
  complementaryProducts: Product[];
  stylingTip: string;
}> {
  return get<{
    mainProduct: Product;
    complementaryProducts: Product[];
    stylingTip: string;
  }>(`/api/ai/outfit/${productId}`);
}

/**
 * AI-powered product search
 * Endpoint: POST /ai/search
 */
export async function aiSearch(data: {
  query: string;
  filters?: {
    categoryId?: number;
    minPrice?: number;
    maxPrice?: number;
  };
  limit?: number;
}): Promise<ProductRecommendation[]> {
  return post<ProductRecommendation[]>('/ai/search', data);
}

/**
 * Get trending products based on AI analysis
 * Endpoint: GET /ai/trending?limit={limit}
 */
export async function getTrendingProducts(limit: number = 10): Promise<ProductRecommendation[]> {
  return get<ProductRecommendation[]>(`/ai/trending?limit=${limit}`);
}

// ==================== AUTHENTICATED ENDPOINTS ====================

/**
 * Get recommendations based on user's purchase history
 * Endpoint: GET /ai/recommendations/history
 * Requires: Authentication
 */
export async function getHistoryBasedRecommendations(
  limit: number = 10
): Promise<ProductRecommendation[]> {
  return get<ProductRecommendation[]>(`/ai/recommendations/history?limit=${limit}`);
}

/**
 * Get recommendations based on user's wishlist
 * Endpoint: GET /ai/recommendations/wishlist
 * Requires: Authentication
 */
export async function getWishlistBasedRecommendations(
  limit: number = 10
): Promise<ProductRecommendation[]> {
  return get<ProductRecommendation[]>(`/ai/recommendations/wishlist?limit=${limit}`);
}

// ==================== ADMIN ENDPOINTS ====================

/**
 * Get user clustering data
 * Endpoint: GET /ai/clustering/users
 * Requires: ADMIN role
 */
export async function getUserClusters(): Promise<UserCluster[]> {
  return get<UserCluster[]>('/ai/clustering/users');
}

/**
 * Get product clustering data
 * Endpoint: GET /ai/clustering/products
 * Requires: ADMIN role
 */
export async function getProductClusters(): Promise<ProductCluster[]> {
  return get<ProductCluster[]>('/ai/clustering/products');
}

/**
 * Trigger user clustering analysis
 * Endpoint: POST /ai/clustering/users/analyze
 * Requires: ADMIN role
 */
export async function analyzeUserClusters(): Promise<{
  message: string;
  clustersFound: number;
  totalUsers: number;
}> {
  return post<{
    message: string;
    clustersFound: number;
    totalUsers: number;
  }>('/ai/clustering/users/analyze', {});
}

/**
 * Trigger product clustering analysis
 * Endpoint: POST /ai/clustering/products/analyze
 * Requires: ADMIN role
 */
export async function analyzeProductClusters(): Promise<{
  message: string;
  clustersFound: number;
  totalProducts: number;
}> {
  return post<{
    message: string;
    clustersFound: number;
    totalProducts: number;
  }>('/ai/clustering/products/analyze', {});
}
