/**
 * Shop Page - Product Listing with Filters
 * Strategy: ISR (Incremental Static Regeneration)
 * Revalidate: 30 minutes
 */

import { searchProductsWithFilters, getAllSizes, getAllColors } from '@/lib/api/products';
import { getAllActiveCategories } from '@/lib/api/categories';
import ShopPageClient from '@/components/shop/ShopPageClient';

// ISR: Revalidate every 30 minutes
export const revalidate = 1800;

interface ShopPageProps {
  searchParams: {
    page?: string;
    size?: string;
    category?: string;
    minPrice?: string;
    maxPrice?: string;
    brand?: string;
    search?: string;
    sort?: string;
    gender?: string;
    size_filter?: string;
    color?: string;
  };
}

export default async function ShopPage({ searchParams }: ShopPageProps) {
  const page = parseInt(searchParams.page || '0');
  const size = parseInt(searchParams.size || '20');

  // Fetch products with all filters using search endpoint
  const products = await searchProductsWithFilters({
    page,
    size,
    categoryId: searchParams.category ? parseInt(searchParams.category) : undefined,
    minPrice: searchParams.minPrice ? parseFloat(searchParams.minPrice) : undefined,
    maxPrice: searchParams.maxPrice ? parseFloat(searchParams.maxPrice) : undefined,
    brand: searchParams.brand,
    search: searchParams.search,
    gender: searchParams.gender,
    size_filter: searchParams.size_filter,
    color: searchParams.color,
    sort: searchParams.sort || 'createdAt,desc',
  });

  // Fetch all categories, sizes, and colors for filter sidebar
  const [categories, availableSizes, availableColors] = await Promise.all([
    getAllActiveCategories(),
    getAllSizes().catch(() => []),
    getAllColors().catch(() => []),
  ]);

  return (
    <ShopPageClient
      products={products}
      categories={categories}
      availableSizes={availableSizes}
      availableColors={availableColors}
      currentPage={page}
      filters={{
        category: searchParams.category,
        minPrice: searchParams.minPrice,
        maxPrice: searchParams.maxPrice,
        brand: searchParams.brand,
        search: searchParams.search,
        gender: searchParams.gender,
        size: searchParams.size_filter,
        color: searchParams.color,
        sort: searchParams.sort,
      }}
    />
  );
}

export const metadata = {
  title: 'Shop - All Products',
  description: 'Browse our wide selection of fashion products with the latest styles and trends',
};
