/**
 * Admin Product API Client
 * Handles all admin product management API calls
 */

import { get, post, put, patch, del } from './client';

// ==================== TYPES ====================

export interface ProductImage {
  id: number;
  imageUrl: string;
  cloudinaryPublicId: string;
  altText?: string;
  isPrimary: boolean;
  displayOrder: number;
  folderPath?: string;
  thumbnailUrl?: string;
}

export interface Product {
  id: number;
  name: string;
  slug?: string;
  sku?: string;
  shortDescription?: string;
  description?: string;
  price: number;
  compareAtPrice?: number;
  stockQuantity: number;
  active: boolean;
  featured?: boolean;
  availableSizes?: string[];
  availableColors?: string[];
  brand?: string;
  material?: string;
  gender?: string;
  viewCount?: number;
  averageRating?: number;
  reviewCount?: number;
  soldCount?: number;
  metaTitle?: string;
  metaDescription?: string;
  metaKeywords?: string;
  categories?: Category[];
  images?: ProductImage[];
  createdAt?: string;
  updatedAt?: string;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  description?: string;
}

export interface ProductsResponse {
  products: Product[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
}

export interface ProductStats {
  totalProducts: number;
  activeProducts: number;
  inactiveProducts: number;
  outOfStock: number;
}

export interface GetProductsParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDirection?: 'ASC' | 'DESC';
  active?: boolean;
}

export interface SearchProductsParams {
  query: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDirection?: 'ASC' | 'DESC';
}

export interface BulkPriceUpdateRequest {
  productIds: number[];
  action: 'increase' | 'decrease';
  percentage: number;
}

export interface UpdateCategoriesRequest {
  categoryIds: number[];
}

// ==================== API FUNCTIONS ====================

export const adminProductsApi = {
  /**
   * Get all products with pagination and filtering
   * GET /api/admin/products
   */
  getAll: async (params: GetProductsParams = {}): Promise<ProductsResponse> => {
    const queryParams = new URLSearchParams();

    if (params.page !== undefined) queryParams.append('page', params.page.toString());
    if (params.size !== undefined) queryParams.append('size', params.size.toString());
    if (params.sortBy) queryParams.append('sortBy', params.sortBy);
    if (params.sortDirection) queryParams.append('sortDirection', params.sortDirection);
    if (params.active !== undefined) queryParams.append('active', params.active.toString());

    return get<ProductsResponse>(`/api/admin/products?${queryParams.toString()}`);
  },

  /**
   * Search products (includes inactive products)
   * GET /api/admin/products/search
   */
  search: async (params: SearchProductsParams): Promise<ProductsResponse> => {
    const queryParams = new URLSearchParams();

    queryParams.append('query', params.query);
    if (params.page !== undefined) queryParams.append('page', params.page.toString());
    if (params.size !== undefined) queryParams.append('size', params.size.toString());
    if (params.sortBy) queryParams.append('sortBy', params.sortBy);
    if (params.sortDirection) queryParams.append('sortDirection', params.sortDirection);

    return get<ProductsResponse>(`/api/admin/products/search?${queryParams.toString()}`);
  },

  /**
   * Get product by ID
   * GET /api/admin/products/{productId}
   */
  getById: async (productId: number): Promise<Product> => {
    return get<Product>(`/api/admin/products/${productId}`);
  },

  /**
   * Create new product
   * POST /api/admin/products
   */
  create: async (product: Partial<Product>): Promise<{ message: string; product: Product }> => {
    return post<{ message: string; product: Product }>('/api/admin/products', product);
  },

  /**
   * Update product
   * PUT /api/admin/products/{productId}
   */
  update: async (productId: number, updates: Partial<Product>): Promise<{ message: string; product: Product }> => {
    return put<{ message: string; product: Product }>(`/api/admin/products/${productId}`, updates);
  },

  /**
   * Update product stock
   * PATCH /api/admin/products/{productId}/stock
   */
  updateStock: async (productId: number, stockQuantity: number): Promise<{ message: string; productId: number; stockQuantity: number }> => {
    return patch<{ message: string; productId: number; stockQuantity: number }>(
      `/api/admin/products/${productId}/stock`,
      { stockQuantity }
    );
  },

  /**
   * Update product status (activate/deactivate)
   * PATCH /api/admin/products/{productId}/status
   */
  updateStatus: async (productId: number, active: boolean): Promise<{ message: string; productId: number; active: boolean }> => {
    return patch<{ message: string; productId: number; active: boolean }>(
      `/api/admin/products/${productId}/status`,
      { active }
    );
  },

  /**
   * Delete product (soft delete - set active to false)
   * DELETE /api/admin/products/{productId}
   */
  delete: async (productId: number): Promise<{ message: string; productId: number }> => {
    return del<{ message: string; productId: number }>(`/api/admin/products/${productId}`);
  },

  /**
   * Get product statistics
   * GET /api/admin/products/stats
   */
  getStats: async (): Promise<ProductStats> => {
    return get<ProductStats>('/api/admin/products/stats');
  },

  /**
   * Bulk update product prices
   * POST /api/admin/products/bulk-price-update
   */
  bulkPriceUpdate: async (request: BulkPriceUpdateRequest): Promise<{ message: string; updatedCount: number; totalRequested: number; errors?: string[] }> => {
    return post<{ message: string; updatedCount: number; totalRequested: number; errors?: string[] }>(
      '/api/admin/products/bulk-price-update',
      request
    );
  },

  /**
   * Update product categories (replace)
   * PATCH /api/admin/products/{productId}/categories
   */
  updateCategories: async (productId: number, categoryIds: number[]): Promise<{ message: string; productId: number; categories: Category[] }> => {
    return patch<{ message: string; productId: number; categories: Category[] }>(
      `/api/admin/products/${productId}/categories`,
      { categoryIds }
    );
  },

  /**
   * Add categories to product (append)
   * POST /api/admin/products/{productId}/categories
   */
  addCategories: async (productId: number, categoryIds: number[]): Promise<{ message: string; productId: number; addedCount: number; totalCategories: number; categories: Category[] }> => {
    return post<{ message: string; productId: number; addedCount: number; totalCategories: number; categories: Category[] }>(
      `/api/admin/products/${productId}/categories`,
      { categoryIds }
    );
  },

  /**
   * Remove categories from product
   * DELETE /api/admin/products/{productId}/categories
   */
  removeCategories: async (productId: number, categoryIds: number[]): Promise<{ message: string; productId: number; removedCount: number; remainingCategories: number; categories: Category[] }> => {
    return del<{ message: string; productId: number; removedCount: number; remainingCategories: number; categories: Category[] }>(
      `/api/admin/products/${productId}/categories`,
      { categoryIds }
    );
  },
};
