/**
 * Homepage - E-commerce Fashion Store
 * Strategy: ISR (Incremental Static Regeneration)
 * Revalidate: 1 hour
 */

import Link from 'next/link';
import { getFeaturedProducts, getNewestProducts, getBestSellers, getSaleProducts } from '@/lib/api/products';
import { getRootCategories } from '@/lib/api/categories';
import HeroCarousel from '@/components/home/HeroCarousel';
import NewsletterForm from '@/components/home/NewsletterForm';
import { ProductSection } from '@/components/product';
import TrendingProducts from '@/components/product/TrendingProducts';

// ISR: Revalidate every 1 hour
export const revalidate = 3600;

export const metadata = {
  title: 'Cửa Hàng Thời Trang - Điểm Đến Mua Sắm Của Bạn',
  description: 'Khám phá xu hướng thời trang mới nhất với gợi ý từ AI. Quần áo chất lượng, phụ kiện và nhiều hơn nữa.',
  keywords: 'thời trang, quần áo, phụ kiện, mua sắm online, thời trang nam nữ, áo thun, giày, coolmate, thời trang việt nam',
  openGraph: {
    title: 'Cửa Hàng Thời Trang - Điểm Đến Mua Sắm Của Bạn',
    description: 'Khám phá xu hướng thời trang mới nhất với gợi ý từ AI. Quần áo chất lượng, phụ kiện và nhiều hơn nữa.',
    type: 'website',
    siteName: 'Coolmate Fashion',
  },
};

// Hero banners data (Coolmate style - full banner images)
const heroBanners = [
  {
    id: '1',
    title: 'Amazon Best Sellers',
    ctaText: 'MUA NGAY',
    ctaLink: '/shop/amazon-bestsellers',
    imageUrl: '/images/ama_Hero_banner_Desktop_-_1920x788.jpg'
  },
  {
    id: '2',
    title: 'Small But Mighty',
    ctaText: 'MUA NGAY',
    ctaLink: '/collections/small-but-mighty',
    imageUrl: '/images/NTO2_Frame_88129__2_.jpg'
  },
  {
    id: '3',
    title: 'Teamwear Collection',
    ctaText: 'MUA NGAY',
    ctaLink: '/collections/teamwear',
    imageUrl: '/images/TW_Hero_Desktop.jpg'
  },
  {
    id: '4',
    title: 'Fall Winter',
    ctaText: 'MUA NGAY',
    ctaLink: '/shop/fall-winter',
    imageUrl: '/images/Home.jpg'
  }
];

export default async function HomePage() {
  // Fetch all data in parallel for better performance
  const [featuredProducts, newestProducts, bestSellers, saleProducts, categories] = await Promise.all([
    getFeaturedProducts(0, 8).catch((err) => {
      console.error('Error fetching featured products:', err);
      return { content: [], totalElements: 0, totalPages: 0, size: 0, number: 0 };
    }),
    getNewestProducts(0, 8).catch((err) => {
      console.error('Error fetching newest products:', err);
      return { content: [], totalElements: 0, totalPages: 0, size: 0, number: 0 };
    }),
    getBestSellers(0, 8).catch((err) => {
      console.error('Error fetching bestsellers:', err);
      return { content: [], totalElements: 0, totalPages: 0, size: 0, number: 0 };
    }),
    getSaleProducts(0, 8).catch((err) => {
      console.error('Error fetching sale products:', err);
      return { content: [], totalElements: 0, totalPages: 0, size: 0, number: 0 };
    }),
    getRootCategories().catch((err) => {
      console.error('Error fetching categories:', err);
      return [];
    }),
  ]);

  console.log('Homepage data fetched:', {
    featuredCount: featuredProducts.content.length,
    newestCount: newestProducts.content.length,
    bestSellersCount: bestSellers.content.length,
    saleCount: saleProducts.content.length,
    categoriesCount: categories.length,
  });

  return (
    <div className="min-h-screen bg-white">
      {/* Hero Carousel - Client Component */}
      <HeroCarousel banners={heroBanners} autoPlayInterval={5000} />

      {/* Featured Products - Server Component */}
      {featuredProducts.content.length > 0 && (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
          <ProductSection
            title="Sản Phẩm Nổi Bật"
            icon="⭐"
            products={featuredProducts.content.slice(0, 4)}
            viewAllLink="/shop?featured=true"
            badge="NỔI BẬT"
            columns={4}
          />
        </div>
      )}

      {/* Best Sellers - Server Component */}
      {bestSellers.content.length > 0 && (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
          <ProductSection
            title="Bán Chạy Nhất"
            icon="🔥"
            products={bestSellers.content.slice(0, 4)}
            viewAllLink="/shop?bestsellers=true"
            badge="BEST SELLER"
            columns={4}
          />
        </div>
      )}

      {/* Trending Products - AI Powered - Client Component */}
      <TrendingProducts />

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-12">
              {/* Men Wear Banner */}
              <Link href="/product/men" className="group relative overflow-hidden rounded-2xl shadow-lg">
                <div className="aspect-[4/3] relative">
                  <img
                    src="/images/menwear.jpg"
                    alt="Men Wear"
                    className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/20 to-transparent"></div>
                  <div className="absolute bottom-0 left-0 p-8 text-white">
                    <h3 className="text-4xl font-bold mb-2 uppercase tracking-wider">MEN WEAR</h3>
                    <p className="text-lg mb-4 opacity-90">Nhập COOLNEW Giảm 50K đơn đầu tiên từ 299k</p>
                    <button className="bg-white text-black px-6 py-2.5 rounded-md font-semibold uppercase tracking-wide hover:bg-gray-100 transition-colors">
                      Khám Phá
                    </button>
                  </div>
                </div>
              </Link>

              {/* Women Active Banner */}
              <Link href="/product/women" className="group relative overflow-hidden rounded-2xl shadow-lg">
                <div className="aspect-[4/3] relative">
                  <img
                    src="/images/womenactive.jpg"
                    alt="Women Active"
                    className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/20 to-transparent"></div>
                  <div className="absolute bottom-0 left-0 p-8 text-white">
                    <h3 className="text-4xl font-bold mb-2 uppercase tracking-wider">WOMEN ACTIVE</h3>
                    <p className="text-lg mb-4 opacity-90">Nhập CMVSEAMLESS Giảm 50K cho BST Seamless</p>
                    <button className="bg-white text-black px-6 py-2.5 rounded-md font-semibold uppercase tracking-wide hover:bg-gray-100 transition-colors">
                      Khám Phá
                    </button>
                  </div>
                </div>
              </Link>
            </div>
      {/* Newest Products - Server Component */}
      {newestProducts.content.length > 0 && (
        <section className="py-16 bg-gray-50">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            {/* Header */}
            <div className="flex items-center justify-between mb-8">
              <h2 className="text-3xl font-bold text-gray-900 flex items-center gap-2">
                ✨ Hàng Mới Về
              </h2>
              <Link
                href="/shop?sort=createdAt,desc"
                className="text-blue-600 hover:text-blue-800 font-medium flex items-center gap-1 group"
              >
                Xem Tất Cả
                <svg
                  className="w-5 h-5 transition-transform group-hover:translate-x-1"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </Link>
            </div>

            {/* Banner Images Grid - Coolmate Style */}
      

            {/* Product Grid */}
            <ProductSection
              title=""
              products={newestProducts.content.slice(0, 4)}
              viewAllLink=""
              badge="MỚI"
              columns={4}
            />
          </div>
        </section>
      )}

      {/* Sale Products - Server Component */}
      {saleProducts.content.length > 0 && (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
          <ProductSection
            title="Đang Giảm Giá"
            icon="💰"
            products={saleProducts.content.slice(0, 4)}
            viewAllLink="/shop?sale=true"
            badge="SALE"
            columns={4}
          />
        </div>
      )}

      {/* Features - Server Component */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
        <h3 className="text-3xl font-bold text-center mb-12 text-gray-900">Tại Sao Chọn Chúng Tôi?</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div className="p-8 rounded-xl shadow-md hover:shadow-lg transition-shadow bg-white">
            <div className="bg-blue-100 w-14 h-14 rounded-lg flex items-center justify-center mb-4">
              <svg className="w-7 h-7 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
              </svg>
            </div>
            <h4 className="text-xl font-semibold mb-2 text-gray-900">Gợi Ý Từ AI</h4>
            <p className="text-gray-600">Đề xuất cá nhân hóa sử dụng Gemini AI.</p>
          </div>
          <div className="p-8 rounded-xl shadow-md hover:shadow-lg transition-shadow bg-white">
            <div className="bg-green-100 w-14 h-14 rounded-lg flex items-center justify-center mb-4">
              <svg className="w-7 h-7 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h4 className="text-xl font-semibold mb-2 text-gray-900">Đảm Bảo Chất Lượng</h4>
            <p className="text-gray-600">100% hàng chính hãng với đánh giá từ khách hàng.</p>
          </div>
          <div className="p-8 rounded-xl shadow-md hover:shadow-lg transition-shadow bg-white">
            <div className="bg-purple-100 w-14 h-14 rounded-lg flex items-center justify-center mb-4">
              <svg className="w-7 h-7 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
            </div>
            <h4 className="text-xl font-semibold mb-2 text-gray-900">Giao Hàng Nhanh</h4>
            <p className="text-gray-600">Vận chuyển nhanh với theo dõi thời gian thực.</p>
          </div>
        </div>
      </section>

     

      {/* Newsletter - Client Component */}
      <section className="py-16 bg-gray-50">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h3 className="text-3xl font-bold mb-4 text-gray-900">Đăng Ký Nhận Tin</h3>
          <p className="text-lg mb-8 text-gray-600">Nhận thông tin mới nhất về sản phẩm và ưu đãi độc quyền!</p>
          <NewsletterForm />
        </div>
      </section>

      {/* CTA - Server Component */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20">
        <div className="bg-gradient-to-r from-blue-600 to-purple-600 rounded-2xl p-12 text-center text-white">
          <h3 className="text-4xl font-bold mb-4">Sẵn Sàng Nâng Cấp Tủ Đồ?</h3>
          <p className="text-xl mb-8 opacity-90">Tham gia cùng hàng nghìn khách hàng hài lòng ngay hôm nay!</p>
          <Link href="/shop" className="bg-white text-blue-600 px-8 py-4 rounded-lg text-lg font-semibold hover:bg-gray-100 transition-colors inline-block">
            Bắt Đầu Mua Sắm Ngay
          </Link>
        </div>
      </section>
    </div>
  );
}
