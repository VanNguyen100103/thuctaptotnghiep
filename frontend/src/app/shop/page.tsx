/**
 * Shop Page - Universal products page with filtering
 * Supports sale products, search, and all filters
 * Strategy: ISR (Incremental Static Regeneration)
 * Revalidate: 30 minutes
 */

import { searchProductsWithFilters, getAllSizes, getAllColors, getSaleProducts } from '@/lib/api/products';
import { getAllActiveCategories } from '@/lib/api/categories';
import ShopPageClient from '@/components/shop/ShopPageClient';

// ISR: Revalidate every 30 minutes
export const revalidate = 1800;

interface ShopPageProps {
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
    sale?: string; // New: filter for sale products
    gender?: string;
  }>;
}

export default async function ShopPage({ searchParams }: ShopPageProps) {
  // Await searchParams (Next.js 15 requirement)
  const params = await searchParams;

  const page = parseInt(params.page || '0');
  const size = parseInt(params.size || '20');
  const isSaleMode = params.sale === 'true';

  // Fetch all categories, sizes, and colors
  const [allCategories, availableSizes, availableColors] = await Promise.all([
    getAllActiveCategories(),
    getAllSizes().catch(() => []),
    getAllColors().catch(() => []),
  ]);

  // Fetch products based on mode
  let products;

  if (isSaleMode) {
    // Sale mode: Use dedicated /api/products/sale endpoint
    // This returns products sorted by discount percentage (highest first)
    products = await getSaleProducts(page, size);
  } else {
    // Normal mode: Use search with filters
    const validCategoryId = params.category ? parseInt(params.category) : undefined;

    products = await searchProductsWithFilters({
      page,
      size,
      categoryId: validCategoryId,
      minPrice: params.minPrice ? parseFloat(params.minPrice) : undefined,
      maxPrice: params.maxPrice ? parseFloat(params.maxPrice) : undefined,
      brand: params.brand,
      search: params.search,
      gender: params.gender,
      size_filter: params.size_filter,
      color: params.color,
      sort: params.sort || 'createdAt,desc',
    });
  }

  // Dynamic title and hero based on mode
  const title = isSaleMode ? 'Sản Phẩm Sale' :
                params.search ? `Tìm kiếm: ${params.search}` :
                'Tất Cả Sản Phẩm';

  const heroTitle = isSaleMode ? '💰 Sản Phẩm Đang Giảm Giá' :
                    params.search ? `🔍 Kết quả tìm kiếm: "${params.search}"` :
                    '🛍️ Tất Cả Sản Phẩm';

  const heroDescription = isSaleMode
    ? 'Săn ngay những deal hot với mức giảm giá hấp dẫn nhất'
    : params.search
    ? `Tìm thấy ${products.totalElements} sản phẩm phù hợp`
    : 'Khám phá toàn bộ bộ sưu tập thời trang của chúng tôi';

  const heroBg = isSaleMode
    ? 'bg-gradient-to-r from-red-600 to-pink-600'
    : 'bg-gradient-to-r from-blue-600 to-indigo-600';

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Hero Banner */}
      <div className={`${heroBg} text-white py-16`}>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h1 className="text-4xl md:text-5xl font-bold mb-4">{heroTitle}</h1>
          <p className="text-lg md:text-xl opacity-90">
            {heroDescription}
          </p>
          {isSaleMode && (
            <div className="mt-6 inline-flex items-center gap-2 bg-white/20 backdrop-blur-sm px-6 py-3 rounded-full">
              <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span className="font-semibold">Số lượng có hạn - Nhanh tay đặt hàng!</span>
            </div>
          )}
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
          gender: params.gender,
          size: params.size_filter,
          color: params.color,
          sort: params.sort || 'createdAt,desc',
        }}
        title={title}
        basePath="/shop"
        forceBadge={isSaleMode ? 'SALE' : undefined}
        isSaleMode={isSaleMode}
      />
    </div>
  );
}

// Dynamic metadata based on mode
export async function generateMetadata({ searchParams }: ShopPageProps) {
  const params = await searchParams;
  const isSaleMode = params.sale === 'true';

  if (isSaleMode) {
    return {
      title: 'Sản Phẩm Sale - Giảm Giá Hot',
      description: 'Săn ngay những deal hot với mức giảm giá hấp dẫn nhất. Cập nhật liên tục các sản phẩm thời trang đang sale.',
      keywords: 'sale quần áo, giảm giá thời trang, discount, khuyến mãi, flash sale, deal hot',
      openGraph: {
        title: 'Sản Phẩm Sale - Giảm Giá Hot',
        description: 'Săn ngay những deal hot với mức giảm giá hấp dẫn nhất. Cập nhật liên tục các sản phẩm thời trang đang sale.',
        type: 'website',
        siteName: 'Coolmate Fashion',
      },
    };
  }

  if (params.search) {
    return {
      title: `Tìm kiếm: ${params.search} - Shop`,
      description: `Kết quả tìm kiếm cho "${params.search}" - Tìm sản phẩm thời trang phù hợp với bạn`,
      keywords: `${params.search}, tìm kiếm thời trang, mua sắm quần áo, cửa hàng online`,
      openGraph: {
        title: `Tìm kiếm: ${params.search} - Shop`,
        description: `Kết quả tìm kiếm cho "${params.search}" - Tìm sản phẩm thời trang phù hợp với bạn`,
        type: 'website',
        siteName: 'Coolmate Fashion',
      },
    };
  }

  return {
    title: 'Shop - Tất Cả Sản Phẩm',
    description: 'Khám phá toàn bộ bộ sưu tập thời trang với đa dạng sản phẩm chất lượng cao',
    keywords: 'mua sắm quần áo, thời trang online, cửa hàng quần áo, shop thời trang, bán quần áo',
    openGraph: {
      title: 'Shop - Tất Cả Sản Phẩm',
      description: 'Khám phá toàn bộ bộ sưu tập thời trang với đa dạng sản phẩm chất lượng cao',
      type: 'website',
      siteName: 'Coolmate Fashion',
    },
  };
}
