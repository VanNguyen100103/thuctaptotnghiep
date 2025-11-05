/**
 * Women's Products Page - Displays products filtered by gender: Nữ
 * Strategy: ISR (Incremental Static Regeneration)
 * Revalidate: 30 minutes
 */

import { searchProductsWithFilters, getAllSizes, getAllColors } from '@/lib/api/products';
import { getAllActiveCategories } from '@/lib/api/categories';
import ShopPageClient from '@/components/shop/ShopPageClient';

// ISR: Revalidate every 30 minutes
export const revalidate = 1800;

interface WomenProductsPageProps {
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

export default async function WomenProductsPage({ searchParams }: WomenProductsPageProps) {
  // Await searchParams (Next.js 15 requirement)
  const params = await searchParams;

  const page = parseInt(params.page || '0');
  const size = parseInt(params.size || '20');

  // Fetch all categories, sizes, and colors (use getAllActiveCategories for consistency)
  const [allCategories, availableSizes, availableColors] = await Promise.all([
    getAllActiveCategories(),
    getAllSizes().catch(() => []),
    getAllColors().catch(() => []),
  ]);

  // Find women's root category by slug instead of hardcoded ID
  const womenRootCategory = allCategories.find(cat => cat.slug === 'thoi-trang-nu');

  // Get women's categories (parentId = womenRootCategory.id OR is the root itself)
  const womenCategories = womenRootCategory
    ? allCategories.filter(cat => cat.parentId === womenRootCategory.id || cat.id === womenRootCategory.id)
    : [];
  const womenCategoryIds = womenCategories.map(cat => cat.id);

  // Find category by slug if provided, otherwise by id
  let validCategoryId: number | undefined = undefined;
  if (params.category) {
    // Check if it's a slug (contains letters) or id (only numbers)
    if (/^\d+$/.test(params.category)) {
      // It's an ID
      const categoryId = parseInt(params.category);
      validCategoryId = womenCategoryIds.includes(categoryId) ? categoryId : undefined;
    } else {
      // It's a slug
      const category = allCategories.find(cat => cat.slug === params.category);
      if (category && womenCategoryIds.includes(category.id)) {
        validCategoryId = category.id;
      }
    }
  }

  // Fetch products filtered by gender: Nữ
  const products = await searchProductsWithFilters({
    page,
    size,
    categoryId: validCategoryId,
    minPrice: params.minPrice ? parseFloat(params.minPrice) : undefined,
    maxPrice: params.maxPrice ? parseFloat(params.maxPrice) : undefined,
    brand: params.brand,
    search: params.search,
    gender: 'Nữ', // Fixed gender filter
    size_filter: params.size_filter,
    color: params.color,
    sort: params.sort || 'createdAt,desc',
  });

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Hero Banner */}
      <div className="bg-gradient-to-r from-pink-500 to-purple-600 text-white py-16">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h1 className="text-4xl md:text-5xl font-bold mb-4">👗 Thời Trang Nữ</h1>
          <p className="text-lg md:text-xl opacity-90">
            Khám phá bộ sưu tập thời trang nữ thanh lịch, sang trọng
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
          gender: 'Nữ',
          size: params.size_filter,
          color: params.color,
          sort: params.sort || 'createdAt,desc',
        }}
        title="Thời Trang Nữ"
        basePath="/category/nu"
      />
    </div>
  );
}

export const metadata = {
  title: 'Thời Trang Nữ - Women\'s Fashion',
  description: 'Khám phá bộ sưu tập thời trang nữ thanh lịch, sang trọng với đa dạng sản phẩm chất lượng',
  keywords: 'thời trang nữ, quần áo nữ, áo thun nữ, váy nữ, phụ kiện nữ, thời trang nữ online, đồ nữ đẹp',
  openGraph: {
    title: 'Thời Trang Nữ - Women\'s Fashion',
    description: 'Khám phá bộ sưu tập thời trang nữ thanh lịch, sang trọng với đa dạng sản phẩm chất lượng',
    type: 'website',
    siteName: 'Coolmate Fashion',
  },
};
