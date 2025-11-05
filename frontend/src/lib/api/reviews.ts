/**
 * Review API Client
 * Handles all product review API calls to Spring Boot backend
 */

import { get, post, put, del } from './client';
import type { Review, ReviewListResponse, CreateReviewRequest, UpdateReviewRequest } from '@/types/review';

// ==================== PUBLIC ENDPOINTS ====================

/**
 * Get reviews for a product
 * Endpoint: GET /api/reviews/product/{productId}?page={page}&size={size}&sort={sort}
 */
export async function getProductReviews(
  productId: number,
  page: number = 0,
  size: number = 10,
  sort: string = 'createdAt,desc'
): Promise<ReviewListResponse> {
  return get<ReviewListResponse>(
    `/api/reviews/product/${productId}?page=${page}&size=${size}&sort=${sort}`
  );
}

/**
 * Get review statistics for a product
 * Endpoint: GET /api/reviews/product/{productId}/statistics
 */
export async function getProductReviewStatistics(productId: number): Promise<{
  totalReviews: number;
  averageRating: number;
  ratingDistribution: {
    1: number;
    2: number;
    3: number;
    4: number;
    5: number;
  };
  verifiedPurchaseCount: number;
}> {
  return get<{
    totalReviews: number;
    averageRating: number;
    ratingDistribution: {
      1: number;
      2: number;
      3: number;
      4: number;
      5: number;
    };
    verifiedPurchaseCount: number;
  }>(`/api/reviews/product/${productId}/statistics`);
}

/**
 * Get review by ID
 * Endpoint: GET /api/reviews/{id}
 */
export async function getReviewById(id: number): Promise<Review> {
  return get<Review>(`/api/reviews/${id}`);
}

/**
 * Get reviews by rating
 * Endpoint: GET /api/reviews/product/{productId}/rating/{rating}
 */
export async function getReviewsByRating(
  productId: number,
  rating: number,
  page: number = 0,
  size: number = 10
): Promise<ReviewListResponse> {
  return get<ReviewListResponse>(
    `/api/reviews/product/${productId}/rating/${rating}?page=${page}&size=${size}`
  );
}

/**
 * Get verified reviews
 * Endpoint: GET /api/reviews/product/{productId}/verified
 */
export async function getVerifiedReviews(
  productId: number,
  page: number = 0,
  size: number = 10
): Promise<ReviewListResponse> {
  return get<ReviewListResponse>(
    `/api/reviews/product/${productId}/verified?page=${page}&size=${size}`
  );
}

/**
 * Get reviews with images
 * Endpoint: GET /api/reviews/product/{productId}/with-images
 */
export async function getReviewsWithImages(
  productId: number,
  page: number = 0,
  size: number = 10
): Promise<ReviewListResponse> {
  return get<ReviewListResponse>(
    `/api/reviews/product/${productId}/with-images?page=${page}&size=${size}`
  );
}

/**
 * Get most helpful reviews
 * Endpoint: GET /api/reviews/product/{productId}/helpful
 */
export async function getMostHelpfulReviews(
  productId: number,
  limit: number = 5
): Promise<Review[]> {
  return get<Review[]>(`/api/reviews/product/${productId}/helpful?limit=${limit}`);
}

// ==================== AUTHENTICATED ENDPOINTS ====================

/**
 * Get user's reviews
 * Endpoint: GET /api/reviews/user/me?page={page}&size={size}
 * Requires: Authentication
 */
export async function getUserReviews(page: number = 0, size: number = 10): Promise<ReviewListResponse> {
  return get<ReviewListResponse>(`/api/reviews/user/me?page=${page}&size=${size}`);
}

/**
 * Check if user can review a product
 * Endpoint: GET /api/reviews/product/{productId}/can-review
 * Requires: Authentication
 */
export async function canReviewProduct(productId: number): Promise<{
  canReview: boolean;
  reason?: string;
  orderId?: number;
}> {
  return get<{
    canReview: boolean;
    reason?: string;
    orderId?: number;
  }>(`/api/reviews/product/${productId}/can-review`);
}

/**
 * Create a new review
 * Endpoint: POST /api/reviews
 * Requires: Authentication
 */
export async function createReview(data: CreateReviewRequest): Promise<Review> {
  return post<Review>('/api/reviews', data);
}

/**
 * Update a review
 * Endpoint: PUT /api/reviews/{id}
 * Requires: Authentication (must be review owner)
 */
export async function updateReview(id: number, data: UpdateReviewRequest): Promise<Review> {
  return put<Review>(`/api/reviews/${id}`, data);
}

/**
 * Delete a review
 * Endpoint: DELETE /api/reviews/{id}
 * Requires: Authentication (must be review owner)
 */
export async function deleteReview(id: number): Promise<void> {
  return del<void>(`/api/reviews/${id}`);
}

/**
 * Mark review as helpful
 * Endpoint: POST /api/reviews/{id}/helpful
 * Requires: Authentication
 */
export async function markReviewHelpful(id: number): Promise<Review> {
  return post<Review>(`/api/reviews/${id}/helpful`, {});
}

/**
 * Report a review
 * Endpoint: POST /api/reviews/{id}/report
 * Requires: Authentication
 */
export async function reportReview(id: number, reason: string): Promise<{
  message: string;
  reportId: number;
}> {
  return post<{
    message: string;
    reportId: number;
  }>(`/api/reviews/${id}/report`, { reason });
}

// ==================== ADMIN ENDPOINTS ====================

/**
 * Get all reviews (admin)
 * Endpoint: GET /api/reviews/all
 * Requires: ADMIN role
 */
export async function getAllReviews(page: number = 0, size: number = 20): Promise<ReviewListResponse> {
  return get<ReviewListResponse>(`/api/reviews/all?page=${page}&size=${size}`);
}

/**
 * Respond to a review (admin)
 * Endpoint: POST /api/reviews/{id}/respond
 * Requires: ADMIN role
 */
export async function respondToReview(id: number, response: string): Promise<Review> {
  return post<Review>(`/api/reviews/${id}/respond`, { response });
}

/**
 * Delete any review (admin)
 * Endpoint: DELETE /api/reviews/{id}/admin
 * Requires: ADMIN role
 */
export async function adminDeleteReview(id: number): Promise<void> {
  return del<void>(`/api/reviews/${id}/admin`);
}
