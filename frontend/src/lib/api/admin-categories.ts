/**
 * Admin Categories API Client
 * Manages category CRUD operations with hierarchical support
 */

import { get, put, del } from './client';

// Use empty string for relative paths when using nginx proxy
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || '';

export interface Category {
  id: number;
  name: string;
  slug: string;
  description?: string;
  imageUrl?: string;
  active: boolean;
  displayOrder: number;
  parentId?: number;
  childrenCount: number;
  children?: Category[];
}

export interface CategoryTreeResponse {
  categories: Category[];
  total: number;
}

export interface CategoryResponse {
  message: string;
  category: Category;
}

export const adminCategoriesApi = {
  /**
   * Get all active categories
   */
  getAll: async (): Promise<CategoryTreeResponse> => {
    return get<CategoryTreeResponse>('/api/categories');
  },

  /**
   * Get category tree (hierarchical structure)
   */
  getTree: async (): Promise<CategoryTreeResponse> => {
    return get<CategoryTreeResponse>('/api/categories/tree');
  },

  /**
   * Get root categories (no parent)
   */
  getRootCategories: async (): Promise<CategoryTreeResponse> => {
    return get<CategoryTreeResponse>('/api/categories/root');
  },

  /**
   * Get category by ID
   */
  getById: async (id: number): Promise<Category> => {
    return get<Category>(`/api/categories/${id}`);
  },

  /**
   * Get category by slug
   */
  getBySlug: async (slug: string): Promise<Category> => {
    return get<Category>(`/api/categories/slug/${slug}`);
  },

  /**
   * Get subcategories of a parent
   */
  getChildren: async (parentId: number): Promise<CategoryTreeResponse> => {
    return get<CategoryTreeResponse>(`/api/categories/${parentId}/children`);
  },

  /**
   * Create new category with optional image
   */
  create: async (data: {
    name: string;
    slug: string;
    description?: string;
    displayOrder?: number;
    parentId?: number;
    image?: File;
  }): Promise<CategoryResponse> => {
    const formData = new FormData();
    formData.append('name', data.name);
    formData.append('slug', data.slug);
    if (data.description) formData.append('description', data.description);
    if (data.displayOrder !== undefined) formData.append('displayOrder', data.displayOrder.toString());
    if (data.parentId) formData.append('parentId', data.parentId.toString());
    if (data.image) formData.append('image', data.image);

    // Don't use apiRequest for FormData - it adds Content-Type: application/json
    // Use fetch directly with proper headers
    const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;

    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // Get CSRF token for POST request
    const csrfResponse = await fetch(`${API_BASE_URL}/api/auth/csrf-token`, {
      credentials: 'include',
      headers: token ? { 'Authorization': `Bearer ${token}` } : {},
    });
    if (csrfResponse.ok) {
      const csrfData = await csrfResponse.json();
      if (csrfData.token) {
        headers['X-XSRF-TOKEN'] = csrfData.token;
      }
    }

    const response = await fetch(`${API_BASE_URL}/api/categories`, {
      method: 'POST',
      body: formData,
      credentials: 'include',
      headers, // Don't set Content-Type - let browser set it for FormData
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.error || `HTTP ${response.status}`);
    }

    return response.json();
  },

  /**
   * Update category
   */
  update: async (id: number, data: {
    name?: string;
    slug?: string;
    description?: string;
    imageUrl?: string;
    active?: boolean;
    displayOrder?: number;
    parentId?: number | null;
  }): Promise<CategoryResponse> => {
    return put<CategoryResponse>(`/api/categories/${id}`, data);
  },

  /**
   * Delete category (soft delete)
   */
  delete: async (id: number): Promise<{ message: string; categoryId: number; imageDeleted: boolean }> => {
    return del<{ message: string; categoryId: number; imageDeleted: boolean }>(`/api/categories/${id}`);
  },

  /**
   * Upload category image
   */
  uploadImage: async (id: number, image: File): Promise<CategoryResponse & { imageUrl: string }> => {
    const formData = new FormData();
    formData.append('image', image);

    // Use fetch directly for FormData
    const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;

    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // Get CSRF token
    const csrfResponse = await fetch(`${API_BASE_URL}/api/auth/csrf-token`, {
      credentials: 'include',
      headers: token ? { 'Authorization': `Bearer ${token}` } : {},
    });
    if (csrfResponse.ok) {
      const csrfData = await csrfResponse.json();
      if (csrfData.token) {
        headers['X-XSRF-TOKEN'] = csrfData.token;
      }
    }

    const response = await fetch(`${API_BASE_URL}/api/categories/${id}/image`, {
      method: 'POST',
      body: formData,
      credentials: 'include',
      headers,
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.error || `HTTP ${response.status}`);
    }

    return response.json();
  },

  /**
   * Delete category image
   */
  deleteImage: async (id: number): Promise<{ message: string; categoryId: number; deletedFromCloudinary: boolean }> => {
    return del<{ message: string; categoryId: number; deletedFromCloudinary: boolean }>(`/api/categories/${id}/image`);
  },
};
