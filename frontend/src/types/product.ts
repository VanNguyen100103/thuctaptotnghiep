/**
 * Product Types
 */

export interface ProductImage {
  id: number;
  imageUrl: string;
  cloudinaryPublicId: string;
  altText: string;
  isPrimary: boolean;
  displayOrder: number;
  folderPath: string;
  thumbnailUrl: string;
  color?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Product {
  id: number;
  name: string;
  slug: string;
  description: string;
  shortDescription?: string;
  price: number;
  compareAtPrice?: number;
  discountPrice?: number;
  discountPercentage?: number;
  stockQuantity: number;
  sku: string;
  brand?: string;
  material?: string;
  gender?: string;
  weight?: number;
  dimensions?: string;
  rating?: number;
  averageRating?: number;
  reviewCount?: number;
  soldCount?: number;
  viewCount?: number;
  images: ProductImage[];
  availableSizes?: string[];
  availableColors?: string[];
  categoryId?: number;
  categoryName?: string;
  categories?: any[];
  status?: 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK';
  active?: boolean;
  featured?: boolean;
  newArrival?: boolean;
  inStock?: boolean;
  metaTitle?: string;
  metaDescription?: string;
  metaKeywords?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProductListResponse {
  content: Product[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface ProductFilterParams {
  page?: number;
  size?: number;
  sort?: string;
  categoryId?: number;
  minPrice?: number;
  maxPrice?: number;
  brand?: string;
  search?: string;
  gender?: string;
  size_filter?: string;
  color?: string;
  status?: 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK';
  featured?: boolean;
  newArrival?: boolean;
}
