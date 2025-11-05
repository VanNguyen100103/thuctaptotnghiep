/**
 * Sports Products Page - Displays products in category: Thể Thao
 * Strategy: ISR (Incremental Static Regeneration)
 * Revalidate: 30 minutes
 */

import { searchProductsWithFilters, getAllSizes, getAllColors } from '@/lib/api/products';
import { getAllActiveCategories } from '@/lib/api/categories';
import ShopPageClient from '@/components/shop/ShopPageClient';

// ISR: Revalidate every 30 minutes
export const revalidate = 1800;

interface SportsProductsPageProps {
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
    gender?: string;
  }>;
}

export default async function SportsProductsPage({ searchParams }: SportsProductsPageProps) {
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

  // Find sports root category by slug
  const sportsRootCategory = allCategories.find(cat => cat.slug === 'the-thao');

  // Get sports sub-categories for sidebar display
  const sportsSubCategories = sportsRootCategory
    ? allCategories.filter(cat => cat.parentId === sportsRootCategory.id)
    : [];

  // Get all categories that belong to sports (root + sub-categories)
  const allSportsCategoryIds = sportsRootCategory
    ? [sportsRootCategory.id, ...sportsSubCategories.map(cat => cat.id)]
    : [];

  // Find category by slug if provided, otherwise by id
  let validCategoryId: number | undefined = undefined;
  if (params.category) {
    // Check if it's a slug (contains letters) or id (only numbers)
    if (/^\d+$/.test(params.category)) {
      // It's an ID
      const categoryId = parseInt(params.category);
      validCategoryId = allSportsCategoryIds.includes(categoryId) ? categoryId : undefined;
    } else {
      // It's a slug - find category in all categories
      const category = allCategories.find(cat => cat.slug === params.category);
      if (category && allSportsCategoryIds.includes(category.id)) {
        validCategoryId = category.id;
      }
    }
  }

  // If no specific category, use the sports root category
  if (!validCategoryId && sportsRootCategory) {
    validCategoryId = sportsRootCategory.id;
  }

  // Pass category to ShopPageClient only if it's a sub-category (not root sports category)
  // Root sports category filtering is handled by validCategoryId in server-side fetch
  // and will be maintained by passing the category to the categories prop
  const categoryForFiltering = (params.category && params.category !== 'the-thao')
    ? params.category
    : (sportsRootCategory ? sportsRootCategory.slug : undefined);

  // Fetch products filtered by sports category
  const products = await searchProductsWithFilters({
    page,
    size,
    categoryId: validCategoryId,
    minPrice: params.minPrice ? parseFloat(params.minPrice) : undefined,
    maxPrice: params.maxPrice ? parseFloat(params.maxPrice) : undefined,
    brand: params.brand,
    search: params.search,
    gender: params.gender, // Optional gender filter
    size_filter: params.size_filter,
    color: params.color,
    sort: params.sort || 'createdAt,desc',
  });

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Hero Banner */}
      <div className="bg-gradient-to-r from-green-600 to-blue-600 text-white py-16">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h1 className="text-4xl md:text-5xl font-bold mb-4">⚽ Thể Thao</h1>
          <p className="text-lg md:text-xl opacity-90">
            Các đồ thể thao phù hợp nhu cầu vận động của quý khách
          </p>
        </div>
      </div>

      {/* Products Grid */}
      <ShopPageClient
        products={products}
        categories={sportsRootCategory ? [sportsRootCategory, ...sportsSubCategories] : sportsSubCategories}
        availableSizes={availableSizes}
        availableColors={availableColors}
        currentPage={page}
        filters={{
          category: categoryForFiltering, // Always pass slug for client-side filtering
          minPrice: params.minPrice,
          maxPrice: params.maxPrice,
          brand: params.brand,
          search: params.search,
          gender: params.gender,
          size: params.size_filter,
          color: params.color,
          sort: params.sort || 'createdAt,desc',
        }}
        title="Thể Thao"
        basePath="/category/the-thao"
      />
    </div>
  );
}

export const metadata = {
  title: 'Thể Thao - Sports Fashion',
  description: 'Các đồ thể thao phù hợp nhu cầu vận động của quý khách - Đa dạng sản phẩm chất lượng',
  keywords: 'thể thao, quần áo thể thao, áo tập, giày thể thao, dụng cụ thể thao, gym, fitness, sportswear',
  openGraph: {
    title: 'Thể Thao - Sports Fashion',
    description: 'Các đồ thể thao phù hợp nhu cầu vận động của quý khách - Đa dạng sản phẩm chất lượng',
    type: 'website',
    siteName: 'Coolmate Fashion',
  },
};
