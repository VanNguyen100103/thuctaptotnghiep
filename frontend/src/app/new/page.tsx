/**
 * NEW Products Page - Displays newest arrivals
 * Strategy: ISR (Incremental Static Regeneration)
 * Revalidate: 30 minutes
 */

import { searchProductsWithFilters, getAllSizes, getAllColors } from '@/lib/api/products';
import { getAllActiveCategories } from '@/lib/api/categories';
import ShopPageClient from '@/components/shop/ShopPageClient';

// ISR: Revalidate every 30 minutes
export const revalidate = 1800;

interface NewProductsPageProps {
  searchParams: {
    page?: string;
    size?: string;
    category?: string;
    minPrice?: string;
    maxPrice?: string;
    brand?: string;
    search?: string;
    gender?: string;
    size_filter?: string;
    color?: string;
    sort?: string;
  };
}

export default async function NewProductsPage({ searchParams }: NewProductsPageProps) {
  const page = parseInt(searchParams.page || '0');
  const size = parseInt(searchParams.size || '4');

  // Fetch newest products with filters using search endpoint
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
    <div className="min-h-screen bg-gray-50">
      {/* Hero Banner */}
      <div className="bg-gradient-to-r from-blue-600 to-purple-600 text-white py-16">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h1 className="text-4xl md:text-5xl font-bold mb-4">✨ Hàng Mới Về</h1>
          <p className="text-lg md:text-xl opacity-90">
            Khám phá những sản phẩm mới nhất, xu hướng thời trang hot nhất hiện nay
          </p>
        </div>
      </div>

      {/* Products Grid */}
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
          sort: searchParams.sort || 'createdAt,desc',
        }}
        title="Sản Phẩm Mới Nhất"
        basePath="/new"
      />
    </div>
  );
}

export const metadata = {
  title: 'Hàng Mới Về - Newest Arrivals',
  description: 'Khám phá những sản phẩm thời trang mới nhất, xu hướng hot nhất hiện nay',
  keywords: 'hàng mới, sản phẩm mới, xu hướng thời trang, collection mới, hàng mới về, new arrivals',
  openGraph: {
    title: 'Hàng Mới Về - Newest Arrivals',
    description: 'Khám phá những sản phẩm thời trang mới nhất, xu hướng hot nhất hiện nay',
    type: 'website',
    siteName: 'Coolmate Fashion',
  },
};
