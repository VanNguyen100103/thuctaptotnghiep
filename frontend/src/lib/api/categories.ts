/**
 * Category API Client
 * Handles all category-related API calls to Spring Boot backend
 */

import { get, post, put, del } from './client';
import type { Category, CategoryTree } from '@/types/category';

// ==================== PUBLIC ENDPOINTS ====================

/**
 * Get all active categories
 * Endpoint: GET /api/categories
 */
export async function getAllActiveCategories(): Promise<Category[]> {
  const response = await get<{ categories: Category[]; total: number }>('/api/categories');
  return response.categories;
}

/**
 * Get category by ID
 * Endpoint: GET /api/categories/{id}
 */
export async function getCategoryById(id: number): Promise<Category> {
  return get<Category>(`/api/categories/${id}`);
}

/**
 * Get category by slug
 * Endpoint: GET /api/categories/slug/{slug}
 */
export async function getCategoryBySlug(slug: string): Promise<Category> {
  return get<Category>(`/api/categories/slug/${slug}`);
}

/**
 * Get category tree (hierarchical structure)
 * Endpoint: GET /api/categories/tree
 */
export async function getCategoryTree(): Promise<CategoryTree[]> {
  return get<CategoryTree[]>('/api/categories/tree');
}

/**
 * Get root categories (top-level categories)
 * Endpoint: GET /api/categories/root
 */
export async function getRootCategories(): Promise<Category[]> {
  const response = await get<{ total: number; categories: Category[] }>('/api/categories/root');
  return response.categories;
}

/**
 * Get subcategories of a category
 * Endpoint: GET /api/categories/{id}/children
 */
export async function getSubcategories(id: number): Promise<Category[]> {
  const response = await get<{ categories: Category[]; parentId: number; total: number }>(`/api/categories/${id}/children`);
  return response.categories;
}

/**
 * Get category path (breadcrumb)
 * Endpoint: GET /api/categories/{id}/path
 */
export async function getCategoryPath(id: number): Promise<Category[]> {
  return get<Category[]>(`/api/categories/${id}/path`);
}

/**
 * Search categories by name
 * Endpoint: GET /api/categories/search?name={name}
 */
export async function searchCategories(name: string): Promise<Category[]> {
  return get<Category[]>(`/api/categories/search?name=${encodeURIComponent(name)}`);
}

/**
 * Get popular categories
 * Endpoint: GET /api/categories/popular?limit={limit}
 */
export async function getPopularCategories(limit: number = 10): Promise<Category[]> {
  return get<Category[]>(`/api/categories/popular?limit=${limit}`);
}

// ==================== ADMIN ENDPOINTS (Authenticated) ====================

/**
 * Get all categories (including inactive)
 * Endpoint: GET /api/categories/all
 * Requires: ADMIN role
 */
export async function getAllCategories(): Promise<Category[]> {
  return get<Category[]>('/api/categories/all');
}

/**
 * Create new category
 * Endpoint: POST /api/categories
 * Requires: ADMIN role
 */
export async function createCategory(
  category: Omit<Category, 'id' | 'createdAt' | 'updatedAt'>
): Promise<Category> {
  return post<Category>('/api/categories', category);
}

/**
 * Update category
 * Endpoint: PUT /api/categories/{id}
 * Requires: ADMIN role
 */
export async function updateCategory(id: number, category: Partial<Category>): Promise<Category> {
  return put<Category>(`/api/categories/${id}`, category);
}

/**
 * Delete category
 * Endpoint: DELETE /api/categories/{id}
 * Requires: ADMIN role
 */
export async function deleteCategory(id: number): Promise<void> {
  return del<void>(`/api/categories/${id}`);
}
