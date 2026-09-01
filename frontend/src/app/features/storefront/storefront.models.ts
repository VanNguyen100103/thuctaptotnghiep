export interface Store {
  id: number;
  name: string;
  slug: string;
  logoUrl: string | null;
  phone: string | null;
  address: string | null;
}

export interface ProductImage {
  id: number;
  imageUrl: string;
  thumbnailUrl: string | null;
  isPrimary: boolean;
  displayOrder: number;
  color: string | null;
  altText: string | null;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  active: boolean;
  displayOrder: number;
  children: Category[];
}

export interface Product {
  id: number;
  name: string;
  slug: string;
  sku: string;
  shortDescription: string | null;
  description: string | null;
  price: number;
  compareAtPrice: number | null;
  stockQuantity: number;
  active: boolean;
  featured: boolean;
  availableSizes: string[];
  availableColors: string[];
  brand: string | null;
  material: string | null;
  gender: string | null;
  averageRating: number;
  reviewCount: number;
  soldCount: number;
  categories: Category[];
  images: ProductImage[];
  createdAt: string;
  updatedAt: string;
}

export interface ProductPage {
  products: Product[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
}

export type ProductSortBy = 'createdAt' | 'price' | 'soldCount';
export type SortDirection = 'ASC' | 'DESC';
