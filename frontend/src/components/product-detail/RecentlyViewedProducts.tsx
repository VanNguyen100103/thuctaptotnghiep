'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { getRecentlyViewed, type RecentlyViewedProduct } from '@/lib/api/view-history';
import { formatPrice } from '@/utils/format';
import { getColorHex, isLightColor } from '@/utils/colors';

/**
 * RecentlyViewedProducts Component
 * Displays a horizontal carousel of products the user has recently viewed
 * Works for both authenticated and anonymous users
 */
export default function RecentlyViewedProducts() {
  const [products, setProducts] = useState<RecentlyViewedProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadRecentlyViewed();
  }, []);

  const loadRecentlyViewed = async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await getRecentlyViewed(10);

      if (response && response.success && response.data) {
        setProducts(response.data);
      } else {
        // No data available - this is OK, just don't show the section
        setProducts([]);
      }
    } catch (err) {
      // Silent fail - recently viewed is not critical
      // Just log to console for debugging, don't show error to user
      console.warn('[RecentlyViewed] Failed to load:', err);
      setProducts([]);
      setError(null); // Don't set error - just hide the component
    } finally {
      setLoading(false);
    }
  };

  // Don't render if no products
  if (!loading && products.length === 0) {
    return null;
  }

  // Don't render if error occurred
  if (error) {
    return null;
  }

  return (
    <section className="py-12 bg-gray-50">
      <div className="container mx-auto px-4">
        {/* Section Header */}
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-bold text-gray-900">
            Sản phẩm vừa xem
          </h2>
          <p className="text-sm text-gray-500">
            {products.length} sản phẩm
          </p>
        </div>

        {/* Loading State */}
        {loading && (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
            {[...Array(5)].map((_, index) => (
              <div
                key={index}
                className="bg-white rounded-lg shadow-sm overflow-hidden animate-pulse"
              >
                <div className="aspect-square bg-gray-200"></div>
                <div className="p-4 space-y-2">
                  <div className="h-4 bg-gray-200 rounded"></div>
                  <div className="h-4 bg-gray-200 rounded w-2/3"></div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Products Grid */}
        {!loading && products.length > 0 && (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
            {products.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

/**
 * ProductCard Component
 * Individual product card in the recently viewed carousel
 */
function ProductCard({ product }: { product: RecentlyViewedProduct }) {
  const discount = product.compareAtPrice && product.compareAtPrice > product.price
    ? Math.round(((product.compareAtPrice - product.price) / product.compareAtPrice) * 100)
    : 0;

  return (
    <Link
      href={`/product/${product.slug}`}
      className="group bg-white rounded-lg shadow-sm overflow-hidden hover:shadow-md transition-shadow duration-200"
    >
      {/* Product Image */}
      <div className="relative aspect-square overflow-hidden bg-gray-100">
        {product.imageUrl ? (
          <Image
            src={product.imageUrl}
            alt={product.name}
            fill
            sizes="(max-width: 768px) 50vw, (max-width: 1024px) 33vw, 20vw"
            className="object-cover group-hover:scale-105 transition-transform duration-200"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center bg-gray-200">
            <span className="text-gray-400 text-sm">No Image</span>
          </div>
        )}

        {/* Discount Badge */}
        {discount > 0 && (
          <div className="absolute top-2 left-2 bg-red-500 text-white px-2 py-1 rounded-md text-xs font-semibold">
            -{discount}%
          </div>
        )}

        {/* Out of Stock Badge */}
        {!product.inStock && (
          <div className="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center">
            <span className="bg-white text-gray-900 px-3 py-1 rounded-md text-sm font-semibold">
              Hết hàng
            </span>
          </div>
        )}
      </div>

      {/* Product Info */}
      <div className="p-3">
        {/* Product Name */}
        <h3 className="text-sm font-medium text-gray-900 line-clamp-2 mb-2 group-hover:text-blue-600 transition-colors">
          {product.name}
        </h3>

        {/* Brand */}
        {product.brand && (
          <p className="text-xs text-gray-500 mb-2">{product.brand}</p>
        )}

        {/* Rating */}
        {product.reviewCount > 0 && (
          <div className="flex items-center gap-1 mb-2">
            <div className="flex items-center">
              <svg
                className="w-4 h-4 text-yellow-400 fill-current"
                viewBox="0 0 20 20"
              >
                <path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z" />
              </svg>
            </div>
            <span className="text-xs text-gray-600">
              {product.averageRating.toFixed(1)}
            </span>
            <span className="text-xs text-gray-400">
              ({product.reviewCount})
            </span>
          </div>
        )}

        {/* Price */}
        <div className="flex items-center gap-2">
          <span className="text-base font-bold text-gray-900">
            {formatPrice(product.price)}
          </span>
          {product.compareAtPrice && product.compareAtPrice > product.price && (
            <span className="text-xs text-gray-400 line-through">
              {formatPrice(product.compareAtPrice)}
            </span>
          )}
        </div>

        {/* Colors */}
        {product.availableColors && product.availableColors.length > 0 && (
          <div className="flex items-center gap-1 mt-2">
            {product.availableColors.slice(0, 4).map((color, index) => {
              const hexColor = getColorHex(color);
              const isLight = isLightColor(hexColor);

              return (
                <div
                  key={index}
                  className={`w-4 h-4 rounded-full border ${isLight ? 'border-gray-400' : 'border-gray-300'}`}
                  style={{ backgroundColor: hexColor }}
                  title={color}
                />
              );
            })}
            {product.availableColors.length > 4 && (
              <span className="text-xs text-gray-500">
                +{product.availableColors.length - 4}
              </span>
            )}
          </div>
        )}
      </div>
    </Link>
  );
}
