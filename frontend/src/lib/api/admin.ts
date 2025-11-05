/**
 * Admin API Functions
 * Handles all admin-specific API calls
 */

import { get, post, put, patch, del } from './client';

export interface DashboardOverview {
  revenue: {
    total: number;
    today: number;
  };
  orders: {
    total: number;
    shipped: number;
    pending: number;
    processing: number;
  };
  products: {
    total: number;
    inactive: number;
    outOfStock: number;
    active: number;
  };
  users: {
    total: number;
    inactive: number;
    active: number;
  };
}

export interface SalesStats {
  date: string;
  sales: number;
  revenue: number;
  orders: number;
}

export interface SalesResponse {
  note: string;
  averageOrderValue: number;
  currentPage: number;
  hasNext: boolean;
  hasPrevious: boolean;
  includedStatuses: string[];
  salesByDate: Record<string, number>;
  totalElements: number;
  totalOrders: number;
  totalPages: number;
  totalSales: number;
  chartData?: SalesStats[]; // Added for transformed data
}

export interface TopProduct {
  productId: number;
  productName: string;
  unitsSold: number;
  revenue: number;
  image?: string;
}

export interface OrderStatusStats {
  status: string;
  count: number;
  totalOrderValue: number;
  isConfirmedRevenue: boolean;
  confirmedRevenue: number;
}

export interface OrderStatusResponse {
  totalOrderValue: number;
  totalConfirmedRevenue: number;
  orderStatusStats: OrderStatusStats[];
}

export interface RecentActivity {
  description: string;
  type: 'ORDER' | 'USER' | 'REVIEW';
  amount?: number;
  email?: string;
  createdAt?: string;
}

export interface AIRevenueAnalysisData {
  period: string;
  periodDays: number;
  totalRevenue: number;
  totalOrders: number;
  averageOrderValue: number;
  peakDay: string;
  peakRevenue: number;
  lowestDay: string;
  lowestRevenue: number;
  growthRate: number;
  firstHalfRevenue: number;
  secondHalfRevenue: number;
  top5Products: string[];
  activeDays: number;
}

export interface AIRevenueAnalysis {
  note: string;
  period: string;
  data: AIRevenueAnalysisData;
  success: boolean;
  generatedAt: string;
  aiAnalysis: string;
}

// Dashboard endpoints
export const dashboardApi = {
  getOverview: async (period: '7days' | '30days' | '90days' | 'year' = '30days'): Promise<DashboardOverview> => {
    return get<DashboardOverview>(`/api/admin/dashboard/overview?period=${period}`);
  },

  getSalesStats: async (
    period: '7days' | '30days' | '90days' | 'year' = '30days',
    page: number = 0,
    size: number = 30
  ): Promise<SalesResponse> => {
    return get<SalesResponse>(
      `/api/admin/dashboard/sales?period=${period}&page=${page}&size=${size}`
    );
  },

  getTopProducts: async (limit: number = 10): Promise<TopProduct[]> => {
    const response = await get<{ topProducts: TopProduct[] }>(`/api/admin/dashboard/top-products?limit=${limit}`);
    return response.topProducts;
  },

  getOrderStatusStats: async (): Promise<OrderStatusResponse> => {
    return get<OrderStatusResponse>('/api/admin/dashboard/order-status-stats');
  },

  getRevenuePieChart: async (): Promise<OrderStatusStats[]> => {
    return get<OrderStatusStats[]>('/api/admin/dashboard/revenue-pie-chart');
  },

  getRecentActivities: async (limit: number = 10): Promise<RecentActivity[]> => {
    const response = await get<{ activities: RecentActivity[]; count: number }>(`/api/admin/dashboard/recent-activities?limit=${limit}`);
    return response.activities;
  },

  getAIRevenueAnalysis: async (period: '7days' | '30days' | '90days' | 'year' = '30days'): Promise<AIRevenueAnalysis> => {
    return get<AIRevenueAnalysis>(`/api/admin/dashboard/ai-revenue-analysis?period=${period}`);
  },
};

// ==================== USER MANAGEMENT ====================

export interface AdminUser {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  avatarUrl?: string;
  roles: string[];
  enabled: boolean;
  accountNonLocked: boolean;
  failedLoginAttempts: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface UsersResponse {
  users: AdminUser[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
}

export interface UserStats {
  totalUsers: number;
  enabledUsers: number;
  disabledUsers: number;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  roles?: string[];
}

export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNumber?: string;
}

// User management endpoints
export const adminUsersApi = {
  /**
   * Lấy danh sách người dùng với phân trang và lọc
   * GET /api/admin/users
   */
  getAll: async (params: {
    page?: number;
    size?: number;
    sortBy?: string;
    sortDirection?: 'ASC' | 'DESC';
    search?: string;
    role?: string;
    enabled?: boolean;
  } = {}): Promise<UsersResponse> => {
    const queryParams = new URLSearchParams();

    if (params.page !== undefined) queryParams.append('page', params.page.toString());
    if (params.size !== undefined) queryParams.append('size', params.size.toString());
    if (params.sortBy) queryParams.append('sortBy', params.sortBy);
    if (params.sortDirection) queryParams.append('sortDirection', params.sortDirection);
    if (params.search) queryParams.append('search', params.search);
    if (params.role) queryParams.append('role', params.role);
    if (params.enabled !== undefined) queryParams.append('enabled', params.enabled.toString());

    return get<UsersResponse>(`/api/admin/users?${queryParams.toString()}`);
  },

  /**
   * Lấy thông tin chi tiết người dùng
   * GET /api/admin/users/{userId}
   */
  getById: async (userId: number): Promise<AdminUser> => {
    return get<AdminUser>(`/api/admin/users/${userId}`);
  },

  /**
   * Tạo người dùng mới (admin)
   * POST /api/admin/users
   */
  create: async (data: CreateUserRequest): Promise<{ message: string; user: Partial<AdminUser> }> => {
    return post<{ message: string; user: Partial<AdminUser> }>('/api/admin/users', data);
  },

  /**
   * Cập nhật thông tin người dùng
   * PUT /api/admin/users/{userId}
   */
  update: async (userId: number, data: UpdateUserRequest): Promise<{ message: string; user: AdminUser }> => {
    return put<{ message: string; user: AdminUser }>(`/api/admin/users/${userId}`, data);
  },

  /**
   * Kích hoạt/Vô hiệu hóa tài khoản người dùng
   * PATCH /api/admin/users/{userId}/status
   */
  updateStatus: async (userId: number, enabled: boolean): Promise<{ message: string; userId: number; enabled: boolean }> => {
    return patch<{ message: string; userId: number; enabled: boolean }>(
      `/api/admin/users/${userId}/status`,
      { enabled }
    );
  },

  /**
   * Cập nhật vai trò người dùng
   * PATCH /api/admin/users/{userId}/roles
   */
  updateRoles: async (userId: number, roles: string[]): Promise<{ message: string; userId: number; roles: string[] }> => {
    return patch<{ message: string; userId: number; roles: string[] }>(
      `/api/admin/users/${userId}/roles`,
      { roles }
    );
  },

  /**
   * Xóa người dùng (soft delete - vô hiệu hóa tài khoản)
   * DELETE /api/admin/users/{userId}
   */
  delete: async (userId: number): Promise<{ message: string; userId: number }> => {
    return del<{ message: string; userId: number }>(`/api/admin/users/${userId}`);
  },

  /**
   * Đặt lại mật khẩu người dùng (admin)
   * POST /api/admin/users/{userId}/reset-password
   */
  resetPassword: async (userId: number, newPassword: string): Promise<{ message: string; userId: number }> => {
    return post<{ message: string; userId: number }>(
      `/api/admin/users/${userId}/reset-password`,
      { newPassword }
    );
  },

  /**
   * Lấy thống kê người dùng
   * GET /api/admin/users/stats
   */
  getStats: async (): Promise<UserStats> => {
    return get<UserStats>('/api/admin/users/stats');
  },

  /**
   * Tìm kiếm người dùng
   * GET /api/admin/users/search
   */
  search: async (query: string, page: number = 0, size: number = 20): Promise<UsersResponse> => {
    return get<UsersResponse>(`/api/admin/users/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`);
  },
};

// Product management endpoints
export const adminProductsApi = {
  getAll: async (params: {
    page?: number;
    size?: number;
    sortBy?: string;
    sortDirection?: 'ASC' | 'DESC';
    search?: string;
    status?: boolean;
  } = {}) => {
    const queryString = new URLSearchParams(params as any).toString();
    return get<any>(`/api/admin/products?${queryString}`);
  },

  getById: async (productId: number) => {
    return get<any>(`/api/admin/products/${productId}`);
  },

  create: async (data: any) => {
    return post<any>('/api/admin/products', data);
  },

  update: async (productId: number, data: any) => {
    return put<any>(`/api/admin/products/${productId}`, data);
  },

  updateStock: async (productId: number, quantity: number) => {
    return patch<any>(`/api/admin/products/${productId}/stock`, { quantity });
  },

  updateStatus: async (productId: number, active: boolean) => {
    return patch<any>(`/api/admin/products/${productId}/status`, { active });
  },

  delete: async (productId: number) => {
    return del<any>(`/api/admin/products/${productId}`);
  },

  getStats: async () => {
    return get<any>('/api/admin/products/stats');
  },

  bulkPriceUpdate: async (adjustment: { type: 'INCREASE' | 'DECREASE'; percentage: number }) => {
    return post<any>('/api/admin/products/bulk-price-update', adjustment);
  },
};

// Order management endpoints
export const adminOrdersApi = {
  getAll: async (params: {
    page?: number;
    size?: number;
    status?: string;
  } = {}) => {
    const queryString = new URLSearchParams(params as any).toString();
    return get<any>(`/api/admin/orders?${queryString}`);
  },

  getById: async (orderId: number) => {
    return get<any>(`/api/admin/orders/${orderId}`);
  },

  updateStatus: async (orderId: number, status: string) => {
    return patch<any>(`/api/admin/orders/${orderId}/status`, { status });
  },

  updateTracking: async (orderId: number, trackingNumber: string, carrier: string) => {
    return patch<any>(`/api/admin/orders/${orderId}/tracking`, { trackingNumber, carrier });
  },

  addNotes: async (orderId: number, notes: string) => {
    return patch<any>(`/api/admin/orders/${orderId}/notes`, { notes });
  },

  cancel: async (orderId: number, reason: string) => {
    return post<any>(`/api/admin/orders/${orderId}/cancel`, { reason });
  },

  getStats: async () => {
    return get<any>('/api/admin/orders/stats');
  },

  search: async (query: string) => {
    return get<any>(`/api/admin/orders/search?q=${query}`);
  },

  getRecent: async (limit: number = 10) => {
    return get<any>(`/api/admin/orders/recent?limit=${limit}`);
  },

  getAllowedTransitions: async (orderId: number) => {
    return get<any>(`/api/admin/orders/${orderId}/allowed-transitions`);
  },
};

// Coupon management endpoints
export const adminCouponsApi = {
  getAll: async (params: {
    page?: number;
    size?: number;
  } = {}) => {
    const queryString = new URLSearchParams(params as any).toString();
    return get<any>(`/api/coupons?${queryString}`);
  },

  getById: async (couponId: number) => {
    return get<any>(`/api/coupons/${couponId}`);
  },

  create: async (data: any) => {
    return post<any>('/api/coupons', data);
  },

  update: async (couponId: number, data: any) => {
    return put<any>(`/api/coupons/${couponId}`, data);
  },

  delete: async (couponId: number) => {
    return del<any>(`/api/coupons/${couponId}`);
  },
};

// Review management endpoints
export const adminReviewsApi = {
  getAll: async (params: {
    page?: number;
    size?: number;
    status?: 'PENDING' | 'APPROVED' | 'REJECTED';
  } = {}) => {
    const queryString = new URLSearchParams(params as any).toString();
    return get<any>(`/api/reviews/admin/all?${queryString}`);
  },

  approve: async (reviewId: number) => {
    return patch<any>(`/api/reviews/admin/${reviewId}/approve`, {});
  },

  reject: async (reviewId: number) => {
    return patch<any>(`/api/reviews/admin/${reviewId}/reject`, {});
  },

  delete: async (reviewId: number) => {
    return del<any>(`/api/reviews/admin/${reviewId}`);
  },
};

// Payment management
export const adminPaymentsApi = {
  refund: async (paymentId: number, amount?: number, reason?: string) => {
    return post<any>(`/api/payments/${paymentId}/refund`, { amount, reason });
  },
};
