export interface ProductImage {
  id: number;
  imageUrl: string;
  cloudinaryPublicId: string;
  altText: string | null;
  isPrimary: boolean;
  displayOrder: number;
  folderPath: string | null;
  thumbnailUrl: string | null;
  color: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProductCategoryRef {
  id: number;
  name: string;
  slug: string;
}

export interface ProductDTO {
  id: number;
  name: string;
  slug: string;
  sku: string;
  /** Plain optional scan/print identifier, distinct from sku ("Mã hàng") - KiotViet's "Mã vạch". */
  barcode: string | null;
  shortDescription: string | null;
  description: string | null;
  price: number;
  compareAtPrice: number | null;
  /** Wholesale/purchase cost - store-internal, never present on the public storefront's ProductDTO. */
  costPrice: number | null;
  /** VAT % - OWNER-only, store-internal, never present on the public storefront's ProductDTO. */
  taxRate: number | null;
  stockQuantity: number;
  minStockThreshold: number | null;
  maxStockThreshold: number | null;
  active: boolean;
  featured: boolean;
  availableSizes: string[];
  availableColors: string[];
  brand: string | null;
  material: string | null;
  gender: string | null;
  viewCount: number;
  averageRating: number;
  reviewCount: number;
  soldCount: number;
  metaTitle: string | null;
  metaDescription: string | null;
  metaKeywords: string | null;
  categories: ProductCategoryRef[];
  images: ProductImage[];
  /** Opaque grouping key shared by variant sibling rows generated together; null for ordinary products. */
  variantGroupId: string | null;
  /** Free-named attribute values (e.g. {"Kích cỡ":"M"}) for variant-generated products - see CreateProductVariantsRequest. */
  attributes: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface ProductPage {
  products: ProductDTO[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
}

export interface ProductSearchPage extends ProductPage {
  query: string;
}

export interface ProductStats {
  totalProducts: number;
  activeProducts: number;
  inactiveProducts: number;
  outOfStock: number;
}

export interface CreateProductRequest {
  name: string;
  slug: string;
  sku: string;
  barcode?: string;
  shortDescription?: string;
  description?: string;
  price: number;
  compareAtPrice?: number;
  costPrice?: number;
  taxRate?: number;
  stockQuantity?: number;
  minStockThreshold?: number;
  maxStockThreshold?: number;
  active?: boolean;
  featured?: boolean;
  availableSizes?: string[];
  availableColors?: string[];
  brand?: string;
  material?: string;
  gender?: string;
}

export interface UpdateProductRequest {
  name?: string;
  barcode?: string;
  description?: string;
  price?: number;
  compareAtPrice?: number;
  costPrice?: number;
  taxRate?: number;
  stockQuantity?: number;
  minStockThreshold?: number;
  maxStockThreshold?: number;
  brand?: string;
  material?: string;
  gender?: string;
  availableSizes?: string[];
  availableColors?: string[];
}

export interface VariantRowRequest {
  /** Keys must exactly match CreateProductVariantsRequest.attributeOrder. */
  attributeValues: Record<string, string>;
  sku: string;
  price: number;
  costPrice?: number;
  stockQuantity: number;
}

export interface CreateProductVariantsRequest {
  name: string;
  shortDescription?: string;
  description?: string;
  categoryIds?: number[];
  brand?: string;
  material?: string;
  gender?: string;
  /** Axis names in display order, e.g. ["Kích cỡ", "Màu sắc"] - up to 3. */
  attributeOrder: string[];
  compareAtPrice?: number;
  taxRate?: number;
  minStockThreshold?: number;
  maxStockThreshold?: number;
  active?: boolean;
  featured?: boolean;
  variants: VariantRowRequest[];
}

export interface CreateProductVariantsResponse {
  message: string;
  variantGroupId: string;
  products: ProductDTO[];
}

export interface AdminCategory {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  active: boolean;
  displayOrder: number;
  parentId: number | null;
  childrenCount: number;
}

export type ProductAdminSortBy = 'createdAt' | 'name' | 'price' | 'stockQuantity';
export type SortDirection = 'ASC' | 'DESC';
export type ActiveFilter = 'all' | 'active' | 'inactive';
