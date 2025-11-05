/**
 * Product Detail Client Component
 * Handles client-side interactions for product page
 * Design inspired by Coolmate.me
 */

'use client';

import { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { addToCart } from '@/lib/api/cart';
import { addToWishlist, removeFromWishlistByProductId } from '@/lib/api/wishlist';
import { getSimilarProducts, getOutfitRecommendations } from '@/lib/api/ai';
import { trackView } from '@/lib/api/view-history';
import { getColorHex, isWhiteColor } from '@/utils/colors';
import type { Product } from '@/types/product';
import type { ReviewListResponse } from '@/types/review';
import Link from 'next/link';
import Image from 'next/image';
import ReviewsSection from './ReviewsSection';
import RecentlyViewedProducts from './RecentlyViewedProducts';
import AIRecommendations from './AIRecommendations';
import StylingTips from './StylingTips';
import SizeRecommendation from './SizeRecommendation';
import { cleanAIText } from '@/utils/textUtils';

interface ProductDetailClientProps {
  product: Product;
  reviews?: ReviewListResponse;
  reviewStats?: {
    averageRating: number;
    totalReviews: number;
    ratingDistribution: { [key: string]: number };
    ratingPercentages: { [key: string]: number };
  };
}

export default function ProductDetailClient({
  product,
  reviews = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 0,
    number: 0,
    averageRating: 0,
    ratingDistribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 }
  },
  reviewStats,
}: ProductDetailClientProps) {
  const router = useRouter();
  const { isAuthenticated } = useAuth();
  const queryClient = useQueryClient();

  // Fetch AI data ONLY if user is authenticated
  // GỢI Ý SẢN PHẨM - Similar products
  const { data: similarData } = useQuery({
    queryKey: ['similar-products', product.id],
    queryFn: () => getSimilarProducts(product.id, 4),
    enabled: isAuthenticated, // Only fetch when logged in
    staleTime: 30 * 60 * 1000, // 30 minutes (reduced API calls, backend has cache)
  });

  // TUYỆT HƠN NẾU MẶC CÙNG - Outfit recommendations
  const { data: outfitData } = useQuery({
    queryKey: ['outfit-recommendations', product.id],
    queryFn: () => getOutfitRecommendations(product.id),
    enabled: isAuthenticated, // Only fetch when logged in
    staleTime: 30 * 60 * 1000, // 30 minutes (reduced API calls, backend has cache)
  });

  // Extract products and explanations
  const similar = similarData?.similarProducts || [];
  const similarExplanation = similarData?.aiExplanation || '';
  const outfit = outfitData?.complementaryProducts || [];
  const stylingTip = outfitData?.stylingTip || '';

  const [quantity, setQuantity] = useState(1);
  const [selectedColor, setSelectedColor] = useState<string>('');
  const [selectedSize, setSelectedSize] = useState<string>('');
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);
  const [isInWishlist, setIsInWishlist] = useState(false);
  const similarScrollRef = useRef<HTMLDivElement>(null);
  const outfitScrollRef = useRef<HTMLDivElement>(null);

  // Add to cart mutation
  const addToCartMutation = useMutation({
    mutationFn: () => addToCart({
      productId: product.id,
      quantity,
      size: selectedSize || undefined,
      color: selectedColor || undefined,
    }),
    onSuccess: (response) => {
      // Update cart cache with new data
      queryClient.setQueryData(['cart'], response.cart);
      // Invalidate cart and cart count
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      queryClient.invalidateQueries({ queryKey: ['cartCount'] });
      alert('Đã thêm vào giỏ hàng!');
    },
    onError: (error: any) => {
      if (error.status === 401) {
        router.push('/login?redirect=/product/' + product.slug);
      } else {
        alert('Lỗi: ' + (error.message || 'Không thể thêm vào giỏ hàng'));
      }
    },
  });

  // Wishlist mutations
  const toggleWishlistMutation = useMutation({
    mutationFn: () =>
      isInWishlist ? removeFromWishlistByProductId(product.id) : addToWishlist(product.id),
    onSuccess: () => {
      setIsInWishlist(!isInWishlist);
      queryClient.invalidateQueries({ queryKey: ['wishlist'] });
    },
    onError: (error: any) => {
      if (error.status === 401) {
        router.push('/login?redirect=/shop/' + product.slug);
      }
    },
  });

  // Get images array from product
  const allImages = product.images && product.images.length > 0 ? product.images : [];

  // Filter images by selected color
  const images = selectedColor
    ? allImages.filter((img: any) => img.color === selectedColor || !img.color)
    : allImages;

  const primaryImage = images.find((img: any) => img.isPrimary) || images[0];
  const currentImage = images[selectedImageIndex] || primaryImage;

  // Debug: Log images to console
  console.log('All images:', allImages.length);
  console.log('Filtered images for color', selectedColor, ':', images.length);

  // Calculate discount percentage
  const calculateDiscount = () => {
    if (product.compareAtPrice && product.compareAtPrice > product.price) {
      return Math.round(((product.compareAtPrice - product.price) / product.compareAtPrice) * 100);
    }
    return 0;
  };
  const discount = calculateDiscount();

  // Initialize selected color on mount
  useEffect(() => {
    if (!selectedColor && product.availableColors && product.availableColors.length > 0) {
      setSelectedColor(product.availableColors[0]);
    }
  }, [product.availableColors, selectedColor]);

  // Reset selected image index when color changes
  useEffect(() => {
    setSelectedImageIndex(0);
  }, [selectedColor]);

  // Track product view on mount - with duplicate prevention
  useEffect(() => {
    const trackProductView = async () => {
      // Check if already tracked in this session (prevent duplicate API calls)
      const trackedProducts = JSON.parse(localStorage.getItem('tracked_products') || '[]');

      if (trackedProducts.includes(product.id)) {
        console.log('[ViewTracking] Product already tracked in this session:', product.id);
        return; // Skip API call - already tracked
      }

      // Save to localStorage BEFORE calling API to prevent duplicate calls even if API fails
      trackedProducts.push(product.id);
      localStorage.setItem('tracked_products', JSON.stringify(trackedProducts));

      try {
        await trackView(product.id);
        console.log('[ViewTracking] Product view tracked successfully:', product.id);
      } catch (error) {
        // Silent fail - tracking is not critical for user experience
        console.warn('[ViewTracking] Failed to track view (this is OK, already saved to localStorage):', error);
      }
    };

    trackProductView();
  }, [product.id]);

  const handleAddToCartClick = () => {
    // Check if user is authenticated first
    if (!isAuthenticated) {
      router.push('/login?redirect=/product/' + product.slug);
      return;
    }

    // Validate size selection
    if (product.availableSizes && product.availableSizes.length > 0 && !selectedSize) {
      alert('Vui lòng chọn kích thước');
      return;
    }

    // Validate color selection
    if (product.availableColors && product.availableColors.length > 0 && !selectedColor) {
      alert('Vui lòng chọn màu sắc');
      return;
    }

    addToCartMutation.mutate();
  };
  
  return (
    <div className="min-h-screen bg-white dark:bg-gray-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
        {/* Breadcrumb */}
        <nav className="text-sm mb-4 py-3">
          <Link href="/" className="text-blue-600 hover:underline">
            Trang chủ
          </Link>
          {' / '}
          <Link href={product.gender === 'Nam' ? '/category/nam' : '/category/nu'} className="text-blue-600 hover:underline">
            {product.gender === 'Nam' ? 'Thời Trang Nam' : product.gender === 'Nữ' ? 'Thời Trang Nữ' : 'Sản phẩm'}
          </Link>
          {' / '}
          <span className="text-gray-600 dark:text-gray-400">{product.name}</span>
        </nav>

        {/* Product Info */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-12">
          {/* Images - Vertical Gallery like Coolmate */}
          <div className="flex flex-row gap-4">
            {/* Thumbnail Column (Left) */}
            <div className="flex flex-col gap-2" style={{ width: '80px', flexShrink: 0 }}>
              {images.map((img: any, idx: number) => (
                <button
                  key={img.id || idx}
                  onClick={() => setSelectedImageIndex(idx)}
                  className={`w-20 h-20 rounded-lg overflow-hidden border-2 transition-all ${
                    selectedImageIndex === idx
                      ? 'border-blue-600 ring-2 ring-blue-200'
                      : 'border-gray-200 hover:border-gray-400'
                  }`}
                  style={{ minWidth: '80px', minHeight: '80px' }}
                >
                  <img
                    src={img.imageUrl}
                    alt={`${product.name} ${idx + 1}`}
                    className="w-full h-full object-cover"
                  />
                </button>
              ))}
            </div>

            {/* Main Image (Right) */}
            <div className="flex-1" style={{ minWidth: 0 }}>
              <div className="aspect-square bg-gray-100 dark:bg-gray-800 rounded-lg overflow-hidden top-4">
                {currentImage ? (
                  <img
                    src={currentImage.imageUrl}
                    alt={currentImage.altText || product.name}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <div className="w-full h-full flex items-center justify-center text-gray-400">
                    <span>Không có ảnh</span>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Details */}
          <div className="space-y-4">
            <div>
              <h1 className="text-2xl md:text-3xl font-bold text-gray-900 dark:text-white mb-2">
                {product.name}
              </h1>
              {product.brand && (
                <p className="text-gray-600 dark:text-gray-400">{product.brand}</p>
              )}
            </div>

            {/* Rating */}
            <div className="flex items-center gap-2 pb-3 border-b border-gray-200">
              <div className="flex items-center gap-1">
                {[1, 2, 3, 4, 5].map((star) => (
                  <svg
                    key={star}
                    className={`w-5 h-5 ${
                      star <= Math.floor(product.averageRating || 0)
                        ? 'text-yellow-400'
                        : 'text-gray-300'
                    }`}
                    fill="currentColor"
                    viewBox="0 0 20 20"
                  >
                    <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                  </svg>
                ))}
                <span className="ml-1 text-sm text-gray-600 dark:text-gray-400">
                  ({product.averageRating?.toFixed(1) || '0.0'})
                </span>
              </div>
              {reviews.totalElements > 0 && (
                <button className="text-blue-600 hover:underline text-sm ml-2">
                  {reviews.totalElements} đánh giá
                </button>
              )}
            </div>

            {/* Price */}
            <div className="space-y-1">
              {product.compareAtPrice && product.compareAtPrice > product.price && (
                <div className="text-lg text-gray-400 line-through">
                  {product.compareAtPrice.toLocaleString('vi-VN')}₫
                </div>
              )}
              <div className="flex items-center gap-3">
                <span className="text-3xl font-bold text-gray-900 dark:text-white">
                  {product.price.toLocaleString('vi-VN')}₫
                </span>
                {discount > 0 && (
                  <span className="inline-block px-2 py-1 bg-blue-600 text-white text-sm font-semibold rounded">
                    -{discount}%
                  </span>
                )}
              </div>
            </div>
            {/* Freeship & Discount Info */}
            <div className="space-y-2 py-3 border-t border-b border-gray-200">
              <div className="flex items-center text-sm text-gray-700 dark:text-gray-300">
                <img alt="Miễn phí vận chuyển" loading="lazy" width="18" height="18" decoding="async" data-nimg="1" src="https://www.coolmate.io/images/icons/icon4.svg"/>
                <span className="ml-2 font-medium">Freeship đơn từ 0đ</span>
              </div>
              {discount > 0 && (
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Mã giảm giá</span>
                  <span className="inline-block px-2 py-1 bg-orange-100 text-orange-600 text-xs font-semibold rounded">
                    Giảm {discount}%
                  </span>
                </div>
              )}
            </div>

            {/* Policy Cards - Coolmate Style */}
            <div id="product-detail-policy">
              <ul className="grid grid-cols-2 gap-3 rounded-lg bg-gray-300 p-3">
                {/* Policy 1 */}
                <li className="flex items-center gap-2 lg:gap-3 font-bold">
                  <Image
                    alt="Đổi trả cực dễ chỉ cần số điện thoại"
                    width={28}
                    height={28}
                    className="rounded-lg lg:h-10 lg:w-10"
                    src="/icons/product/return.svg"
                  />
                  <p className="text-left text-xs leading-tight text-gray-900 lg:text-sm">
                    Đổi trả cực dễ chỉ cần<br />số điện thoại
                  </p>
                </li>

                {/* Policy 2 */}
                <li className="flex items-center gap-2 lg:gap-3 font-bold">
                  <Image
                    alt="60 ngày đổi trả"
                    width={28}
                    height={28}
                    className="rounded-lg lg:h-10 lg:w-10"
                    src="/icons/product/return-60.svg"
                  />
                  <p className="text-left text-xs leading-tight text-gray-900 lg:text-sm">
                    60 ngày đổi trả (sản phẩm chưa qua sử dụng và còn nguyên nhãn mác)
                  </p>
                </li>

                {/* Policy 3 */}
                <li className="flex items-center gap-2 lg:gap-3 font-bold">
                  <Image
                    alt="Hotline hỗ trợ"
                    width={28}
                    height={28}
                    className="rounded-lg lg:h-10 lg:w-10"
                    src="/icons/product/phone.svg"
                  />
                  <p className="text-left text-xs leading-tight text-gray-900 lg:text-sm">
                    Hotline 1900.27.27.37<br />hỗ trợ từ 8h30 - 22h
                  </p>
                </li>

                {/* Policy 4 */}
                <li className="flex items-center gap-2 lg:gap-3 font-bold">
                  <Image
                    alt="Đến tận nơi nhận hàng trả"
                    width={28}
                    height={28}
                    className="rounded-lg lg:h-10 lg:w-10"
                    src="/icons/product/location.svg"
                  />
                  <p className="text-left text-xs leading-tight text-gray-900 lg:text-sm">
                    Đến tận nơi nhận hàng trả,<br />hoàn tiền trong 24h
                  </p>
                </li>
              </ul>
            </div>

            {/* Color Selection */}
            {product.availableColors && product.availableColors.length > 0 && (
              <div>
                <p className="text-sm font-medium text-gray-900 dark:text-white mb-2">
                  Màu sắc: <span className="text-gray-600 dark:text-gray-400">{selectedColor || '...'}</span>
                </p>
                <div className="flex flex-wrap gap-3">
                  {product.availableColors.map((colorName: string) => {
                    const colorHex = getColorHex(colorName);
                    const isSelected = selectedColor === colorName;
                    const isWhite = isWhiteColor(colorHex);
                    return (
                      <button
                        key={colorName}
                        onClick={() => setSelectedColor(colorName)}
                        className={`w-8 h-8 rounded-full border-2 transition-all ${
                          isSelected
                            ? 'border-blue-600 ring-2 ring-blue-200 dark:ring-blue-800 scale-110'
                            : 'border-gray-300 hover:border-gray-400 dark:border-gray-600'
                        }`}
                        style={{ backgroundColor: colorHex }}
                        title={colorName}
                        aria-label={colorName}
                      >
                        {isWhite && (
                          <div className="w-full h-full rounded-full border border-gray-200" />
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Size Selection */}
            {product.availableSizes && product.availableSizes.length > 0 && (
              <div>
                <div className="flex items-center justify-between mb-2">
                  <p className="text-sm font-medium text-gray-900 dark:text-white">
                    Kích thước: <span className="text-gray-600 dark:text-gray-400">{selectedSize || '...'}</span>
                  </p>
                  <button className="text-sm text-blue-600 hover:underline">
                    Hướng dẫn chọn size
                  </button>
                </div>
                <div className="grid grid-cols-4 md:grid-cols-6 gap-2">
                  {product.availableSizes.map((size: string) => (
                    <button
                      key={size}
                      onClick={() => setSelectedSize(size)}
                      className={`py-3 rounded-lg border-2 transition-all font-medium ${
                        selectedSize === size
                          ? 'border-blue-600 bg-blue-600 text-white'
                          : 'border-gray-300 hover:border-gray-400 text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800'
                      }`}
                    >
                      {size}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* AI Size Recommendation - After size selection */}
            {product.availableSizes && product.availableSizes.length > 0 && (
              <SizeRecommendation productId={product.id} />
            )}

            {/* Stock */}
            <div>
              {product.stockQuantity > 0 ? (
                <p className="text-green-600 text-sm">
                  Còn hàng ({product.stockQuantity} sản phẩm)
                </p>
              ) : (
                <p className="text-red-600 font-semibold">Hết hàng</p>
              )}
            </div>

            {/* Quantity & Add to Cart */}
            {product.stockQuantity > 0 && (
              <div className="flex items-center gap-4">
                {/* Quantity Selector */}
                <div className="flex items-center bg-gray-100 dark:bg-gray-800 rounded-lg">
                  <button
                    onClick={() => setQuantity(Math.max(1, quantity - 1))}
                    className="px-4 py-3 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-l-lg transition-colors"
                  >
                    −
                  </button>
                  <input
                    type="number"
                    value={quantity}
                    onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                    className="w-16 text-center bg-transparent font-medium"
                    min="1"
                  />
                  <button
                    onClick={() => setQuantity(Math.min(product.stockQuantity, quantity + 1))}
                    className="px-4 py-3 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-r-lg transition-colors"
                  >
                    +
                  </button>
                </div>

                {/* Add to Cart Button */}
                <button
                  onClick={handleAddToCartClick}
                  disabled={product.stockQuantity === 0 || addToCartMutation.isPending}
                  className="flex-1 bg-gray-900 dark:bg-blue-600 text-white px-8 py-3 rounded-lg hover:bg-gray-800 dark:hover:bg-blue-700 transition-colors font-medium flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
                  </svg>
                  <span>{addToCartMutation.isPending ? 'Đang thêm...' : 'Thêm vào giỏ'}</span>
                </button>

                {/* Wishlist Button */}
                <button
                  onClick={() => toggleWishlistMutation.mutate()}
                  className="px-4 py-3 border-2 border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                  title="Yêu thích"
                >
                  {isInWishlist ? '❤️' : '🤍'}
                </button>
              </div>
            )}

            {/* Product Description - Coolmate Style */}

          </div>
        </div>

        {/* AI Styling Tips - Show tips for styling this product */}
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <StylingTips productId={product.id} />
        </div>

        {/* Product Specifications */}
        <section className="mb-16 bg-amber-50 dark:bg-gray-800 rounded-lg p-8">
          <h2 className="text-2xl text-center font-bold text-gray-900 dark:text-white mb-6">
            MÔ TẢ SẢN PHẨM
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {/* Left: Icons & Features */}
            <div className="space-y-6">
              {product.material && (
                <div className="flex items-start gap-4">
                  <div className="w-16 h-16 bg-blue-50 dark:bg-blue-900/20 rounded-lg flex items-center justify-center flex-shrink-0">
                    <svg className="w-8 h-8 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" />
                    </svg>
                  </div>
                  <div className="flex-1">
                    <h3 className="font-semibold text-gray-900 dark:text-white mb-1">Chất liệu</h3>
                    <p className="text-gray-600 dark:text-gray-400">{product.material}</p>
                  </div>
                </div>
              )}
              <div className="flex items-start gap-4">
                <div className="w-16 h-16 bg-blue-50 dark:bg-blue-900/20 rounded-lg flex items-center justify-center flex-shrink-0">
                  <svg className="w-8 h-8 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                </div>
                <div className="flex-1">
                  <h3 className="font-semibold text-gray-900 dark:text-white mb-1">Phù hợp</h3>
                  <p className="text-gray-600 dark:text-gray-400">
                    {product.gender === 'Nam' ? 'Dành cho nam giới' : product.gender === 'Nữ' ? 'Dành cho nữ giới' : 'Phù hợp cho mọi người'}
                  </p>
                </div>
              </div>
              <div className="flex items-start gap-4">
                <div className="w-16 h-16 bg-blue-50 dark:bg-blue-900/20 rounded-lg flex items-center justify-center flex-shrink-0">
                  <svg className="w-8 h-8 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </div>
                <div className="flex-1">
                  <h3 className="font-semibold text-gray-900 dark:text-white mb-1">Bảo quản</h3>
                  <p className="text-gray-600 dark:text-gray-400">
                    Giặt máy nhẹ - Không dùng chất tẩy<br/>
                    Phơi trong bóng mát - Ủi ở nhiệt độ thấp<br/>
                    Không giặt khô
                  </p>
                </div>
              </div>
            </div>

            {/* Mid: Product Details Table */}
            <div className="space-y-4">
              {product.sku && (
                <div className="grid grid-cols-2 border-b dark:border-gray-700 pb-3">
                  <span className="font-semibold text-gray-900 dark:text-white">MÃ SẢN PHẨM</span>
                  <span className="text-gray-600 dark:text-gray-400">{product.sku}</span>
                </div>
              )}
              {product.brand && (
                <div className="grid grid-cols-2 border-b dark:border-gray-700 pb-3">
                  <span className="font-semibold text-gray-900 dark:text-white">THƯƠNG HIỆU</span>
                  <span className="text-gray-600 dark:text-gray-400">{product.brand}</span>
                </div>
              )}
              {product.material && (
                <div className="grid grid-cols-2 border-b dark:border-gray-700 pb-3">
                  <span className="font-semibold text-gray-900 dark:text-white">CHẤT LIỆU</span>
                  <span className="text-gray-600 dark:text-gray-400">{product.material}</span>
                </div>
              )}
              {product.gender && (
                <div className="grid grid-cols-2 border-b dark:border-gray-700 pb-3">
                  <span className="font-semibold text-gray-900 dark:text-white">GIỚI TÍNH</span>
                  <span className="text-gray-600 dark:text-gray-400">{product.gender}</span>
                </div>
              )}
              {product.availableSizes && product.availableSizes.length > 0 && (
                <div className="grid grid-cols-2 border-b dark:border-gray-700 pb-3">
                  <span className="font-semibold text-gray-900 dark:text-white">KÍCH DÁNG</span>
                  <span className="text-gray-600 dark:text-gray-400">Regular Fit</span>
                </div>
              )}
              <div className="grid grid-cols-2 border-b dark:border-gray-700 pb-3">
                <span className="font-semibold text-gray-900 dark:text-white">PHÙ HỢP</span>
                <span className="text-gray-600 dark:text-gray-400">
                  Di chuyển hằng ngày đi làm, đi chơi, hẹn hò
                </span>
              </div>
              <div className="grid grid-cols-2 border-b dark:border-gray-700 pb-3">
                <span className="font-semibold text-gray-900 dark:text-white">TÍNH NĂNG</span>
                <span className="text-gray-600 dark:text-gray-400">
                  Giữ ấm - Cản gió - Thoát mồ hôi<br/>
                  Có túi ẩn bên trong<br/>
                  Dây rút điều chỉnh ở gấu áo giúp ôm vừa vặn
                </span>
              </div>
            </div>

            {/* Right: Product image */}
               <div className="space-y-4">
                <img src={product.images[product.images.length - 1].imageUrl} alt={product.name} className="w-full h-auto rounded-lg" />
               </div>
          </div>

       

     
        </section>



        {/* Reviews Section with Sidebar */}
        <ReviewsSection
          reviews={reviews}
          reviewStats={reviewStats}
          productAverageRating={product.averageRating}
          productId={product.id}
          productName={product.name}
          onRefresh={() => window.location.reload()}
        />

        {/* Similar Products - GỢI Ý SẢN PHẨM - Carousel Style */}
        {similar.length > 0 && (
          <section className="mb-16">
            <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-6">
              SẢN PHẨM TƯƠNG TỰ
            </h2>

            {/* AI Explanation for Similar Products */}
            {similarExplanation && (
              <div className="mb-6 bg-gradient-to-r from-blue-50 to-purple-50 rounded-xl p-4 border-2 border-blue-200 shadow-sm">
                <div className="flex items-start gap-3">
                  <span className="text-2xl flex-shrink-0">💡</span>
                  <div>
                    <h3 className="font-semibold text-gray-900 mb-2">Lý do AI gợi ý:</h3>
                    <p className="text-sm text-gray-700 leading-relaxed">
                      {similarExplanation}
                    </p>
                  </div>
                </div>
              </div>
            )}

            <div className="relative">
              {/* Previous Button */}
              <button
                onClick={() => {
                  if (similarScrollRef.current) {
                    similarScrollRef.current.scrollBy({ left: -400, behavior: 'smooth' });
                  }
                }}
                className="absolute left-0 top-1/2 -translate-y-1/2 z-10 w-10 h-10 bg-white dark:bg-gray-800 rounded-full shadow-lg flex items-center justify-center hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                aria-label="Previous"
              >
                <svg className="w-6 h-6 text-gray-900 dark:text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
              </button>

              {/* Products Carousel */}
              <div
                ref={similarScrollRef}
                className="flex gap-6 overflow-x-auto scroll-smooth hide-scrollbar px-12"
                style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
              >
                {similar.map((item) => {
                  const itemImage = item.images && item.images.length > 0
                    ? item.images.find((img: any) => img.isPrimary)?.imageUrl || item.images[0].imageUrl
                    : 'https://placehold.co/400x400?text=No+Image';
                  const itemDiscount = item.compareAtPrice && item.compareAtPrice > item.price
                    ? Math.round(((item.compareAtPrice - item.price) / item.compareAtPrice) * 100)
                    : 0;
                  return (
                    <Link
                      key={item.id}
                      href={`/product/${item.slug}`}
                      className="group flex-shrink-0 w-64 bg-white dark:bg-gray-800 rounded-lg overflow-hidden shadow-sm hover:shadow-xl transition-all duration-300"
                    >
                      <div className="relative aspect-square overflow-hidden">
                        {itemDiscount > 0 && (
                          <div className="absolute top-2 left-2 z-10 bg-red-600 text-white px-2 py-1 rounded text-xs font-bold">
                            -{itemDiscount}%
                          </div>
                        )}
                        <img
                          src={itemImage}
                          alt={item.name}
                          className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300"
                        />
                        <button className="absolute bottom-2 right-2 w-10 h-10 bg-white dark:bg-gray-800 rounded-full flex items-center justify-center shadow-lg opacity-0 group-hover:opacity-100 transition-opacity">
                          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
                          </svg>
                        </button>
                      </div>
                      <div className="p-4">
                        <h3 className="font-medium text-sm line-clamp-2 text-gray-900 dark:text-white mb-2">
                          {item.name}
                        </h3>
                        <div className="flex items-center gap-2">
                          <p className="text-lg font-bold text-gray-900 dark:text-white">
                            {item.price.toLocaleString('vi-VN')}₫
                          </p>
                          {itemDiscount > 0 && item.compareAtPrice && (
                            <p className="text-sm text-gray-400 line-through">
                              {item.compareAtPrice.toLocaleString('vi-VN')}₫
                            </p>
                          )}
                        </div>
                        {item.averageRating && item.averageRating > 0 && (
                          <div className="flex items-center gap-1 mt-2">
                            <div className="flex">
                              {[1, 2, 3, 4, 5].map((star) => (
                                <svg
                                  key={star}
                                  className={`w-3 h-3 ${
                                    star <= Math.floor(item.averageRating || 0)
                                      ? 'text-yellow-400'
                                      : 'text-gray-300'
                                  }`}
                                  fill="currentColor"
                                  viewBox="0 0 20 20"
                                >
                                  <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                                </svg>
                              ))}
                            </div>
                            <span className="text-xs text-gray-500">({item.reviewCount || 0})</span>
                          </div>
                        )}
                      </div>
                    </Link>
                  );
                })}
              </div>

              {/* Next Button */}
              <button
                onClick={() => {
                  if (similarScrollRef.current) {
                    similarScrollRef.current.scrollBy({ left: 400, behavior: 'smooth' });
                  }
                }}
                className="absolute right-0 top-1/2 -translate-y-1/2 z-10 w-10 h-10 bg-white dark:bg-gray-800 rounded-full shadow-lg flex items-center justify-center hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                aria-label="Next"
              >
                <svg className="w-6 h-6 text-gray-900 dark:text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </button>
            </div>
          </section>
        )}

        {/* Outfit Recommendations - TUYỆT HƠN NẾU MẶC CÙNG - Carousel Style */}
        {outfit.length > 0 && (
          <section className="mb-16">
            <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-6">
              TUYỆT HƠN NẾU MẶC CÙNG
            </h2>

            {/* Styling Tip for Outfit */}
            {stylingTip && (
              <div className="mb-6 bg-gradient-to-r from-purple-50 to-pink-50 rounded-xl p-4 border-2 border-purple-200 shadow-sm">
                <div className="flex items-start gap-3">
                  <span className="text-2xl flex-shrink-0">✨</span>
                  <div>
                    <h3 className="font-semibold text-gray-900 mb-2">Gợi ý phối đồ:</h3>
                    <p className="text-sm text-gray-700 leading-relaxed">
                      {cleanAIText(stylingTip)}
                    </p>
                  </div>
                </div>
              </div>
            )}

            <div className="relative">
              {/* Previous Button */}
              <button
                onClick={() => {
                  if (outfitScrollRef.current) {
                    outfitScrollRef.current.scrollBy({ left: -400, behavior: 'smooth' });
                  }
                }}
                className="absolute left-0 top-1/2 -translate-y-1/2 z-10 w-10 h-10 bg-white dark:bg-gray-800 rounded-full shadow-lg flex items-center justify-center hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                aria-label="Previous"
              >
                <svg className="w-6 h-6 text-gray-900 dark:text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
              </button>

              {/* Products Carousel */}
              <div
                ref={outfitScrollRef}
                className="flex gap-6 overflow-x-auto scroll-smooth hide-scrollbar px-12"
                style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
              >
                {outfit.map((item) => {
                  const itemImage = item.images && item.images.length > 0
                    ? item.images.find((img: any) => img.isPrimary)?.imageUrl || item.images[0].imageUrl
                    : 'https://placehold.co/400x400?text=No+Image';
                  const itemDiscount = item.compareAtPrice && item.compareAtPrice > item.price
                    ? Math.round(((item.compareAtPrice - item.price) / item.compareAtPrice) * 100)
                    : 0;
                  return (
                    <Link
                      key={item.id}
                      href={`/product/${item.slug}`}
                      className="group flex-shrink-0 w-64 bg-white dark:bg-gray-800 rounded-lg overflow-hidden shadow-sm hover:shadow-xl transition-all duration-300"
                    >
                      <div className="relative aspect-square overflow-hidden">
                        {itemDiscount > 0 && (
                          <div className="absolute top-2 left-2 z-10 bg-red-600 text-white px-2 py-1 rounded text-xs font-bold">
                            -{itemDiscount}%
                          </div>
                        )}
                        <img
                          src={itemImage}
                          alt={item.name}
                          className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300"
                        />
                        <button className="absolute bottom-2 right-2 w-10 h-10 bg-white dark:bg-gray-800 rounded-full flex items-center justify-center shadow-lg opacity-0 group-hover:opacity-100 transition-opacity">
                          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
                          </svg>
                        </button>
                      </div>
                      <div className="p-4">
                        <h3 className="font-medium text-sm line-clamp-2 text-gray-900 dark:text-white mb-2">
                          {item.name}
                        </h3>
                        <div className="flex items-center gap-2">
                          <p className="text-lg font-bold text-gray-900 dark:text-white">
                            {item.price.toLocaleString('vi-VN')}₫
                          </p>
                          {itemDiscount > 0 && item.compareAtPrice && (
                            <p className="text-sm text-gray-400 line-through">
                              {item.compareAtPrice.toLocaleString('vi-VN')}₫
                            </p>
                          )}
                        </div>
                        {item.averageRating && item.averageRating > 0 && (
                          <div className="flex items-center gap-1 mt-2">
                            <div className="flex">
                              {[1, 2, 3, 4, 5].map((star) => (
                                <svg
                                  key={star}
                                  className={`w-3 h-3 ${
                                    star <= Math.floor(item.averageRating || 0)
                                      ? 'text-yellow-400'
                                      : 'text-gray-300'
                                  }`}
                                  fill="currentColor"
                                  viewBox="0 0 20 20"
                                >
                                  <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                                </svg>
                              ))}
                            </div>
                            <span className="text-xs text-gray-500">({item.reviewCount || 0})</span>
                          </div>
                        )}
                      </div>
                    </Link>
                  );
                })}
              </div>

              {/* Next Button */}
              <button
                onClick={() => {
                  if (outfitScrollRef.current) {
                    outfitScrollRef.current.scrollBy({ left: 400, behavior: 'smooth' });
                  }
                }}
                className="absolute right-0 top-1/2 -translate-y-1/2 z-10 w-10 h-10 bg-white dark:bg-gray-800 rounded-full shadow-lg flex items-center justify-center hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                aria-label="Next"
              >
                <svg className="w-6 h-6 text-gray-900 dark:text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </button>
            </div>
          </section>
        )}
      </div>

      {/* AI Recommendations Section */}
      <AIRecommendations productId={product.id} />

      {/* Recently Viewed Products Section */}
      <RecentlyViewedProducts />

      {/* Sticky Add to Cart Bar (Mobile) */}
      <div className="fixed bottom-0 left-0 right-0 bg-white dark:bg-gray-900 border-t dark:border-gray-800 p-4 shadow-lg lg:hidden z-50">
        <div className="flex items-center gap-3">
          <div className="flex-shrink-0">
            <img
              src={currentImage?.imageUrl || 'https://placehold.co/400x400?text=No+Image'}
              alt={product.name}
              className="w-16 h-16 object-cover rounded-lg"
            />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-gray-900 dark:text-white truncate">
              {product.name}
            </p>
            <p className="text-lg font-bold text-gray-900 dark:text-white">
              {product.price.toLocaleString('vi-VN')}₫
            </p>
          </div>
          <button
            onClick={handleAddToCartClick}
            disabled={product.stockQuantity === 0 || addToCartMutation.isPending}
            className="flex-shrink-0 bg-gray-900 dark:bg-blue-600 text-white px-6 py-3 rounded-lg font-medium disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {addToCartMutation.isPending ? (
              <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
              </svg>
            ) : (
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
              </svg>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
