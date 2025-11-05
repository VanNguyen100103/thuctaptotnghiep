/**
 * API functions for AI Clustering endpoints
 * These endpoints require ADMIN authentication
 */

import { get } from './client';

/**
 * User metrics for clustering
 */
export interface UserMetrics {
  userId: number;
  username: string;
  orders: number;
  spending: number;
  reviews: number;
}

/**
 * Product metrics for clustering
 */
export interface ProductMetrics {
  productId: number;
  name: string;
  price: number;
  salesVolume: number;
  revenue: number;
  reviewCount: number;
  averageRating: number;
  category: string;
}

/**
 * User clustering response
 */
export interface UserClusteringResponse {
  success: boolean;
  totalUsers: number;
  users: UserMetrics[];
  analysis: string;
  clusteringRules: {
    lowValue: string;
    mediumValue: string;
    highValue: string;
  };
  timestamp: string;
}

/**
 * Product clustering response
 */
export interface ProductClusteringResponse {
  success: boolean;
  totalProducts: number;
  products: ProductMetrics[];
  analysis: string;
  clusteringRules: {
    bestSellers: string;
    moderatePerformers: string;
    lowPerformers: string;
  };
  timestamp: string;
}

/**
 * Order metrics for clustering
 */
export interface OrderMetrics {
  orderId: number;
  orderNumber: string;
  customerName: string;
  totalAmount: number;
  itemCount: number;
  status: string;
  createdAt: string;
}

/**
 * Order clustering response
 */
export interface OrderClusteringResponse {
  success: boolean;
  totalOrders: number;
  rawData: OrderMetrics[];
  analysis: string;
  timestamp: string;
}

/**
 * Fetch user clustering data
 * Requires ADMIN role
 */
export async function getUserClusters(): Promise<UserClusteringResponse> {
  return get<UserClusteringResponse>('/api/ai/clusters/users');
}

/**
 * Fetch product clustering data
 * Requires ADMIN role
 */
export async function getProductClusters(): Promise<ProductClusteringResponse> {
  return get<ProductClusteringResponse>('/api/ai/clusters/products');
}

/**
 * Fetch order clustering data
 * Requires ADMIN role
 */
export async function getOrderClusters(): Promise<OrderClusteringResponse> {
  return get<OrderClusteringResponse>('/api/ai/clusters/orders');
}
