/**
 * Product Card Component (Coolmate Design)
 * Simpler, cleaner design matching Coolmate.me
 */

'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/contexts/AuthContext';
import { addToCart } from '@/lib/api/cart';
import type { Product } from '@/types/product';
import { useRouter } from 'next/navigation';

interface ProductCardProps {
  product: Product;
  badge?: 'BEST SELLER' | 'MỚI' | 'SALE' | 'NỔI BẬT' | null;
}

export default function ProductCard({ product, badge: badgeProp = null }: ProductCardProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuth();
  const [isHovered, setIsHovered] = useState(false);
  const [selectedColor, setSelectedColor] = useState<string | null>(null);

  // Get image based on selected color or primary
  const getDisplayImage = () => {
    if (selectedColor) {
      // Find image with matching color
      const colorImage = product.images?.find(img =>
        img.color && img.color.toLowerCase() === selectedColor.toLowerCase()
      );
      if (colorImage) return colorImage.imageUrl;
    }

    // Fallback to primary or first image
    return product.images?.find(img => img.isPrimary)?.imageUrl ||
           product.images?.[0]?.imageUrl ||
           'https://placehold.co/400x400?text=No+Image';
  };

  const primaryImage = getDisplayImage();

  // Calculate discount
  const discount = product.compareAtPrice && product.compareAtPrice > product.price
    ? Math.round(((product.compareAtPrice - product.price) / product.compareAtPrice) * 100)
    : 0;

  // Determine badge to display
  let badge = badgeProp;

  // Auto-detect badge if not provided (only when undefined, not null)
  if (badgeProp === undefined) {
    // Check if new (within 7 days)
    const isNew = product.createdAt ?
      new Date().getTime() - new Date(product.createdAt).getTime() < 7 * 24 * 60 * 60 * 1000
      : false;

    // Check if best seller (more than 100 reviews or high rating)
    const isBestSeller = (product.reviewCount && product.reviewCount > 100) ||
                         (product.averageRating && product.averageRating >= 4.5);

    if (isBestSeller) badge = 'BEST SELLER';
    else if (isNew) badge = 'MỚI';
    else if (discount > 0) badge = 'SALE';
  }

  // Add to cart mutation
  const addToCartMutation = useMutation({
    mutationFn: (size: string) => addToCart({
      productId: product.id,
      quantity: 1,
      size: size || undefined,
    }),
    onSuccess: (response) => {
      // Update cart cache with new cart data
      queryClient.setQueryData(['cart'], response.cart);
      // Invalidate cart and cart count
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      queryClient.invalidateQueries({ queryKey: ['cartCount'] });
      alert('Đã thêm vào giỏ hàng!');
      setIsHovered(false);
    },
    onError: (error: any) => {
      if (error.status === 401) {
        router.push(`/login?redirect=/product/${product.slug}`);
      } else {
        alert('Lỗi: ' + (error.message || 'Không thể thêm vào giỏ hàng'));
      }
    },
  });

  const handleQuickAdd = (size: string) => {
    // Check authentication before adding to cart
    if (!isAuthenticated) {
      router.push(`/login?redirect=/product/${product.slug}`);
      return;
    }
    addToCartMutation.mutate(size);
  };

  return (
    <div
      className="group relative bg-white dark:bg-gray-800 rounded-xl overflow-hidden transition-all duration-300 hover:shadow-lg"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      {/* Product Image */}
      <Link href={`/product/${product.slug}`} className="block relative">
        <div className="relative aspect-[3/4] overflow-hidden bg-gray-50 dark:bg-gray-700">
          <img
            src={primaryImage}
            alt={product.name}
            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
          />

          {/* Badge */}
          {badge && (
            <div className="absolute top-3 left-3">
              <span className={`inline-block px-3 py-1 text-xs font-bold rounded-md ${
                badge === 'BEST SELLER' ? 'bg-red-600 text-white' :
                badge === 'MỚI' ? 'bg-green-500 text-white' :
                badge === 'NỔI BẬT' ? 'bg-yellow-500 text-white' :
                'bg-orange-500 text-white'
              }`}>
                {badge}
              </span>
            </div>
          )}
        </div>

        {/* Quick Add Size Selector - Shows on Hover */}
        {isHovered && (
          <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 via-black/50 to-transparent px-4 pb-4 pt-16 transition-opacity duration-300 z-10">
            <p className="text-white text-xs font-medium text-center mb-2">
              Thêm nhanh vào giỏ hàng +
            </p>
            <div className="flex gap-2 justify-center flex-wrap">
              {product.availableSizes && product.availableSizes.length > 0 ? (
                product.availableSizes.map((size) => (
                  <button
                    key={size}
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      handleQuickAdd(size);
                    }}
                    disabled={addToCartMutation.isPending}
                    className="min-w-[40px] px-3 py-1.5 bg-white text-gray-900 rounded-md text-sm font-medium hover:bg-gray-100 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {size}
                  </button>
                ))
              ) : (
                // Default sizes when availableSizes is empty
                ['S', 'M', 'L', 'XL'].map((size) => (
                  <button
                    key={size}
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      handleQuickAdd(size);
                    }}
                    disabled={addToCartMutation.isPending}
                    className="min-w-[40px] px-3 py-1.5 bg-white text-gray-900 rounded-md text-sm font-medium hover:bg-gray-100 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {size}
                  </button>
                ))
              )}
            </div>
          </div>
        )}
      </Link>

      {/* Product Info */}
      <div className="p-4">
        {/* Color Swatches */}
        {product.availableColors && product.availableColors.length > 0 && (
          <div className="flex gap-2 mb-3">
            {product.availableColors.slice(0, 5).map((color) => {
              const isSelected = selectedColor === color;
              return (
                <button
                  key={color}
                  onClick={(e) => {
                    e.preventDefault();
                    setSelectedColor(color === selectedColor ? null : color);
                  }}
                  className={`w-6 h-6 rounded-full border-2 cursor-pointer hover:border-gray-500 transition-all ${
                    isSelected ? 'border-blue-600 ring-2 ring-blue-300 scale-110' : 'border-gray-300'
                  }`}
                  style={{
                    backgroundColor: color.toLowerCase() === 'white' || color.toLowerCase() === 'trắng' ? '#ffffff' :
                                    color.toLowerCase() === 'black' || color.toLowerCase() === 'đen' ? '#000000' :
                                    color.toLowerCase() === 'gray' || color.toLowerCase() === 'xám' ? '#9ca3af' :
                                    color.toLowerCase() === 'blue' || color.toLowerCase() === 'xanh' ? '#3b82f6' :
                                    color.toLowerCase() === 'red' || color.toLowerCase() === 'đỏ' ? '#ef4444' :
                                    color.toLowerCase() === 'green' || color.toLowerCase() === 'xanh lá' ? '#10b981' :
                                    color.toLowerCase() === 'yellow' || color.toLowerCase() === 'vàng' ? '#f59e0b' :
                                    color.toLowerCase() === 'pink' || color.toLowerCase() === 'hồng' ? '#ec4899' :
                                    color.toLowerCase() === 'purple' || color.toLowerCase() === 'tím' ? '#8b5cf6' :
                                    color.toLowerCase() === 'navy' || color.toLowerCase() === 'xanh navy' ? '#1e3a8a' :
                                    color.toLowerCase() === 'olive' ? '#808000' :
                                    color.toLowerCase() === 'xanh rêu' ? '#6B8E23' :
                                    color.toLowerCase() === 'be' ? '#F5F5DC' :
                                    color.toLowerCase() === 'xám đậm' ? '#4B5563' :
                                    color.toLowerCase() === 'đỏ đô' ? '#DC143C' :
                                    color.toLowerCase() === 'hồng pastel' ? '#FFD1DC' :
                                    color.toLowerCase() === 'xanh nhạt' ? '#ADD8E6' :
                                    color.toLowerCase() === 'kem' ? '#FFFDD0' :
                                    '#d1d5db'
                  }}
                  title={color}
                  aria-label={`Select ${color} color`}
                />
              );
            })}
            {product.availableColors.length > 5 && (
              <span className="text-xs text-gray-500 self-center">+{product.availableColors.length - 5}</span>
            )}
          </div>
        )}

        {/* Product Name */}
        <Link href={`/shop/${product.slug}`}>
          <h3 className="text-sm font-normal text-gray-900 dark:text-white line-clamp-2 mb-2 hover:text-blue-600 dark:hover:text-blue-400 transition-colors min-h-[40px]">
            {product.name}
          </h3>
        </Link>

        {/* Price */}
        <div className="flex items-baseline gap-2">
          <span className="text-lg font-semibold text-blue-600 dark:text-blue-400">
            {product.price.toLocaleString('vi-VN')} ₫
          </span>
          {discount > 0 && product.compareAtPrice && (
            <span className="text-sm text-gray-400 line-through">
              {product.compareAtPrice.toLocaleString('vi-VN')} ₫
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
