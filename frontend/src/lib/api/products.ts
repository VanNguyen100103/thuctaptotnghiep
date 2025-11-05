/**
 * Product API Client
 * Handles all product-related API calls to Spring Boot backend
 */

import { get, post, put, del } from './client';
import type { Product, ProductListResponse, ProductFilterParams } from '@/types/product';

// ==================== PUBLIC ENDPOINTS ====================

/**
 * Get all active products with pagination and filters
 * Endpoint: GET /api/products
 */
export async function getAllActiveProducts(
  params: ProductFilterParams = {}
): Promise<ProductListResponse> {
  const queryParams = new URLSearchParams();

  if (params.page !== undefined) queryParams.append('page', params.page.toString());
  if (params.size !== undefined) queryParams.append('size', params.size.toString());
  if (params.sort) queryParams.append('sort', params.sort);
  if (params.categoryId) queryParams.append('categoryId', params.categoryId.toString());
  if (params.minPrice !== undefined) queryParams.append('minPrice', params.minPrice.toString());
  if (params.maxPrice !== undefined) queryParams.append('maxPrice', params.maxPrice.toString());
  if (params.brand) queryParams.append('brand', params.brand);
  if (params.search) queryParams.append('search', params.search);

  const query = queryParams.toString();
  return get<ProductListResponse>(`/api/products${query ? `?${query}` : ''}`);
}

/**
 * Get product by ID
 * Endpoint: GET /api/products/{id}
 */
export async function getProductById(id: number): Promise<Product> {
  return get<Product>(`/api/products/${id}`);
}

/**
 * Get product by slug
 * Endpoint: GET /api/products/slug/{slug}
 */
export async function getProductBySlug(slug: string): Promise<Product> {
  return get<Product>(`/api/products/slug/${slug}`);
}

/**
 * Search products by name
 * Endpoint: GET /api/products/search?name={name}&page={page}&size={size}
 */
export async function searchProducts(
  name: string,
  page: number = 0,
  size: number = 20
): Promise<ProductListResponse> {
  return get<ProductListResponse>(
    `/api/products/search?name=${encodeURIComponent(name)}&page=${page}&size=${size}`
  );
}

/**
 * Search and filter products with all parameters
 * Endpoint: GET /api/products/search
 * Supports: keyword, categoryId, minPrice, maxPrice, brand, gender, size, color, sort
 */
export async function searchProductsWithFilters(
  params: ProductFilterParams = {}
): Promise<ProductListResponse> {
  const queryParams = new URLSearchParams();

  if (params.page !== undefined) queryParams.append('page', params.page.toString());
  if (params.size !== undefined) queryParams.append('size_param', params.size.toString());
  if (params.search) queryParams.append('keyword', params.search);
  if (params.categoryId) queryParams.append('categoryId', params.categoryId.toString());
  if (params.minPrice !== undefined) queryParams.append('minPrice', params.minPrice.toString());
  if (params.maxPrice !== undefined) queryParams.append('maxPrice', params.maxPrice.toString());
  if (params.brand) queryParams.append('brand', params.brand);
  if (params.gender) queryParams.append('gender', params.gender);
  if (params.size_filter) queryParams.append('size', params.size_filter);
  if (params.color) queryParams.append('color', params.color);

  // Handle sort parameter
  if (params.sort) {
    const [sortBy, sortDirection] = params.sort.split(',');
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDirection', sortDirection?.toUpperCase() || 'DESC');
  }

  const query = queryParams.toString();
  return get<ProductListResponse>(`/api/products/search${query ? `?${query}` : ''}`);
}

/**
 * Get products by category
 * Endpoint: GET /api/products/category/{categoryId}
 */
export async function getProductsByCategory(
  categoryId: number,
  page: number = 0,
  size: number = 20
): Promise<ProductListResponse> {
  return get<ProductListResponse>(
    `/api/products/category/${categoryId}?page=${page}&size=${size}`
  );
}

/**
 * Get products by price range
 * Endpoint: GET /api/products/price-range?min={min}&max={max}
 */
export async function getProductsByPriceRange(
  min: number,
  max: number,
  page: number = 0,
  size: number = 20
): Promise<ProductListResponse> {
  return get<ProductListResponse>(
    `/api/products/price-range?min=${min}&max=${max}&page=${page}&size=${size}`
  );
}

/**
 * Get featured products
 * Endpoint: GET /api/products/featured
 */
export async function getFeaturedProducts(
  page: number = 0,
  size: number = 10
): Promise<ProductListResponse> {
  return get<ProductListResponse>(`/api/products/featured?page=${page}&size=${size}`);
}

/**
 * Get newest products
 * Endpoint: GET /api/products/newest
 */
export async function getNewestProducts(
  page: number = 0,
  size: number = 10
): Promise<ProductListResponse> {
  return get<ProductListResponse>(`/api/products/newest?page=${page}&size=${size}`);
}

/**
 * Get products by brand
 * Endpoint: GET /api/products/brand/{brand}
 */
export async function getProductsByBrand(
  brand: string,
  page: number = 0,
  size: number = 20
): Promise<ProductListResponse> {
  return get<ProductListResponse>(
    `/api/products/brand/${encodeURIComponent(brand)}?page=${page}&size=${size}`
  );
}

/**
 * Get top rated products
 * Endpoint: GET /api/products/top-rated
 */
export async function getTopRatedProducts(
  page: number = 0,
  size: number = 10
): Promise<ProductListResponse> {
  return get<ProductListResponse>(`/api/products/top-rated?page=${page}&size=${size}`);
}

/**
 * Get bestsellers products
 * Endpoint: GET /api/products/bestsellers
 */
export async function getBestSellers(
  page: number = 0,
  size: number = 10
): Promise<ProductListResponse> {
  return get<ProductListResponse>(`/api/products/bestsellers?page=${page}&size=${size}`);
}

/**
 * Get products on sale (with discount)
 * Endpoint: GET /api/products/sale
 */
export async function getSaleProducts(
  page: number = 0,
  size: number = 10
): Promise<ProductListResponse> {
  return get<ProductListResponse>(`/api/products/sale?page=${page}&size=${size}`);
}

/**
 * Get related products
 * Endpoint: GET /api/products/{id}/related
 */
export async function getRelatedProducts(id: number, limit: number = 4): Promise<Product[]> {
  return get<Product[]>(`/api/products/${id}/related?limit=${limit}`);
}

/**
 * Check product availability
 * Endpoint: GET /api/products/{id}/availability
 */
export async function checkProductAvailability(
  id: number
): Promise<{ available: boolean; quantity: number }> {
  return get<{ available: boolean; quantity: number }>(`/api/products/${id}/availability`);
}

// ==================== ADMIN ENDPOINTS (Authenticated) ====================

/**
 * Create new product
 * Endpoint: POST /api/products
 * Requires: ADMIN role
 */
export async function createProduct(product: Omit<Product, 'id' | 'createdAt' | 'updatedAt'>): Promise<Product> {
  return post<Product>('/api/products', product);
}

/**
 * Update product
 * Endpoint: PUT /api/products/{id}
 * Requires: ADMIN role
 */
export async function updateProduct(id: number, product: Partial<Product>): Promise<Product> {
  return put<Product>(`/api/products/${id}`, product);
}

/**
 * Delete product
 * Endpoint: DELETE /api/products/{id}
 * Requires: ADMIN role
 */
export async function deleteProduct(id: number): Promise<void> {
  return del<void>(`/api/products/${id}`);
}

/**
 * Get all available sizes from active products
 * Endpoint: GET /api/products/filters/sizes
 */
export async function getAllSizes(): Promise<string[]> {
  return get<string[]>('/api/products/filters/sizes');
}

/**
 * Get all available colors from active products
 * Endpoint: GET /api/products/filters/colors
 */
export async function getAllColors(): Promise<string[]> {
  return get<string[]>('/api/products/filters/colors');
}

/**
 * Get all available brands from active products
 * Endpoint: GET /api/products/filters/brands
 */
export async function getAllBrands(): Promise<string[]> {
  return get<string[]>('/api/products/filters/brands');
}
