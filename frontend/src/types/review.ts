/**
 * Review Types
 */

export interface Review {
  id: number;
  productId: number;
  productName?: string;
  userId: number;
  username: string;
  userAvatarUrl?: string;
  rating: number;
  title?: string;
  comment: string;
  verified: boolean;
  approved?: boolean;
  helpfulCount: number;
  reportCount: number;
  images?: ReviewImage[];
  response?: {
    text: string;
    respondedBy: string;
    respondedAt: string;
  };
  createdAt: string;
  updatedAt: string;
}

export interface ReviewImage {
  id: number;
  imageUrl: string;
  displayOrder: number;
}

export interface ReviewListResponse {
  content: Review[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  averageRating: number;
  ratingDistribution: {
    1: number;
    2: number;
    3: number;
    4: number;
    5: number;
  };
}

export interface CreateReviewRequest {
  productId: number;
  rating: number;
  title?: string;
  comment: string;
  images?: string[];
}

export interface UpdateReviewRequest {
  rating?: number;
  title?: string;
  comment?: string;
  images?: string[];
}
