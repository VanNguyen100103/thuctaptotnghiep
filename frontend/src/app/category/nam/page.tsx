/**
 * Men's Products Page - Displays products filtered by gender: Nam
 * Strategy: ISR (Incremental Static Regeneration)
 * Revalidate: 30 minutes
 */

import { searchProductsWithFilters, getAllSizes, getAllColors } from '@/lib/api/products';
import { getAllActiveCategories } from '@/lib/api/categories';
import ShopPageClient from '@/components/shop/ShopPageClient';

// ISR: Revalidate every 30 minutes
export const revalidate = 1800;

interface MenProductsPageProps {
  searchParams: Promise<{
    page?: string;
    size?: string;
    category?: string;
    minPrice?: string;
    maxPrice?: string;
    brand?: string;
    search?: string;
    size_filter?: string;
    color?: string;
    sort?: string;
  }>;
}

export default async function MenProductsPage({ searchParams }: MenProductsPageProps) {
  // Await searchParams (Next.js 15 requirement)
  const params = await searchParams;

  const page = parseInt(params.page || '0');
  const size = parseInt(params.size || '20');

  // Fetch all categories, sizes, and colors
  const [allCategories, availableSizes, availableColors] = await Promise.all([
    getAllActiveCategories(),
    getAllSizes().catch(() => []),
    getAllColors().catch(() => []),
  ]);

  // Find men's root category by slug instead of hardcoded ID
  const menRootCategory = allCategories.find(cat => cat.slug === 'thoi-trang-nam');

  // Get men's categories (parentId = menRootCategory.id OR is the root itself)
  const menCategories = menRootCategory
    ? allCategories.filter(cat => cat.parentId === menRootCategory.id || cat.id === menRootCategory.id)
    : [];
  const menCategoryIds = menCategories.map(cat => cat.id);

  // Find category by slug if provided, otherwise by id
  let validCategoryId: number | undefined = undefined;
  if (params.category) {
    // Check if it's a slug (contains letters) or id (only numbers)
    if (/^\d+$/.test(params.category)) {
      // It's an ID
      const categoryId = parseInt(params.category);
      validCategoryId = menCategoryIds.includes(categoryId) ? categoryId : undefined;
    } else {
      // It's a slug
      const category = allCategories.find(cat => cat.slug === params.category);
      if (category && menCategoryIds.includes(category.id)) {
        validCategoryId = category.id;
      }
    }
  }

  // Fetch products filtered by gender: Nam
  const products = await searchProductsWithFilters({
    page,
    size,
    categoryId: validCategoryId,
    minPrice: params.minPrice ? parseFloat(params.minPrice) : undefined,
    maxPrice: params.maxPrice ? parseFloat(params.maxPrice) : undefined,
    brand: params.brand,
    search: params.search,
    gender: 'Nam', // Fixed gender filter
    size_filter: params.size_filter,
    color: params.color,
    sort: params.sort || 'createdAt,desc',
  });

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Hero Banner */}
      <div className="bg-gradient-to-r from-blue-600 to-blue-800 text-white py-16">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h1 className="text-4xl md:text-5xl font-bold mb-4">👔 Thời Trang Nam</h1>
          <p className="text-lg md:text-xl opacity-90">
            Khám phá bộ sưu tập thời trang nam phong cách, năng động
          </p>
        </div>
      </div>

      {/* Products Grid */}
      <ShopPageClient
        products={products}
        categories={allCategories}
        availableSizes={availableSizes}
        availableColors={availableColors}
        currentPage={page}
        filters={{
          category: params.category,
          minPrice: params.minPrice,
          maxPrice: params.maxPrice,
          brand: params.brand,
          search: params.search,
          gender: 'Nam',
          size: params.size_filter,
          color: params.color,
          sort: params.sort || 'createdAt,desc',
        }}
        title="Thời Trang Nam"
        basePath="/category/nam"
      />
    </div>
  );
}

export const metadata = {
  title: 'Thời Trang Nam - Men\'s Fashion',
  description: 'Khám phá bộ sưu tập thời trang nam phong cách, năng động với đa dạng sản phẩm chất lượng',
  keywords: 'thời trang nam, quần áo nam, áo thun nam, quần nam, phụ kiện nam, thời trang nam online, đồ nam đẹp',
  openGraph: {
    title: 'Thời Trang Nam - Men\'s Fashion',
    description: 'Khám phá bộ sưu tập thời trang nam phong cách, năng động với đa dạng sản phẩm chất lượng',
    type: 'website',
    siteName: 'Coolmate Fashion',
  },
};
