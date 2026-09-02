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
  /** Opaque grouping key shared by variant sibling rows generated together; null for ordinary products. */
  variantGroupId: string | null;
  /** This specific variant's own attribute values, e.g. {"Kích cỡ":"M","Màu sắc":"Đen"}. Empty for non-variant products. */
  attributes: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface ProductPage {
  products: Product[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
}

/** One variant sibling, as returned alongside a variant product's own detail page. */
export interface ProductVariantSummary {
  id: number;
  sku: string;
  /** Free-named attribute values, e.g. {"Kích cỡ":"M","Màu sắc":"Đen"} or {"Hương vị":"Dâu"}. */
  attributes: Record<string, string>;
  price: number;
  compareAtPrice: number | null;
  stockQuantity: number;
  active: boolean;
  imageUrl: string | null;
}

export interface ProductDetailResponse {
  product: Product;
  /** Always present - empty array for non-variant products. */
  variants: ProductVariantSummary[];
}

export type ProductSortBy = 'createdAt' | 'price' | 'soldCount';
export type SortDirection = 'ASC' | 'DESC';
