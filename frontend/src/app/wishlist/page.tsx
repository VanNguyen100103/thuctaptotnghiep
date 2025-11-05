'use client';

import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { useRouter } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import {
  getWishlist,
  removeFromWishlistById,
  clearWishlist,
} from '@/lib/api/wishlist';
import type { WishlistItem } from '@/types/wishlist';

export default function WishlistPage() {
  const { isAuthenticated, loading: authLoading } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [wishlistItems, setWishlistItems] = useState<WishlistItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [removingId, setRemovingId] = useState<number | null>(null);
  const [showClearModal, setShowClearModal] = useState(false);
  const [clearing, setClearing] = useState(false);

  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.push('/login?redirect=/wishlist');
      return;
    }

    if (isAuthenticated) {
      loadWishlist();
    }
  }, [isAuthenticated, authLoading, router]);

  const loadWishlist = async () => {
    try {
      setLoading(true);
      setError('');
      const response = await getWishlist();
      console.log('[Wishlist] Loaded items:', response);
      setWishlistItems(response.wishlistItems);
    } catch (err: any) {
      console.error('[Wishlist] Failed to load:', err);
      setError('Không thể tải danh sách yêu thích');
    } finally {
      setLoading(false);
    }
  };

  const handleRemoveItem = async (wishlistId: number) => {
    setRemovingId(wishlistId);
    try {
      await removeFromWishlistById(wishlistId);
      // Remove from local state
      setWishlistItems(prev => prev.filter(item => item.id !== wishlistId));
      // Invalidate wishlist cache in header to update the badge count
      queryClient.invalidateQueries({ queryKey: ['wishlist'] });
    } catch (err: any) {
      console.error('[Wishlist] Failed to remove item:', err);
      alert('Không thể xóa sản phẩm khỏi danh sách yêu thích');
    } finally {
      setRemovingId(null);
    }
  };

  const handleClearWishlist = async () => {
    setClearing(true);
    try {
      await clearWishlist();
      setWishlistItems([]);
      setShowClearModal(false);
      // Invalidate wishlist cache in header to update the badge count
      queryClient.invalidateQueries({ queryKey: ['wishlist'] });
    } catch (err: any) {
      console.error('[Wishlist] Failed to clear:', err);
      alert('Không thể xóa toàn bộ danh sách yêu thích');
    } finally {
      setClearing(false);
    }
  };

  if (authLoading || loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Danh sách yêu thích</h1>
          <p className="text-gray-600">
            {wishlistItems.length > 0
              ? `Bạn có ${wishlistItems.length} sản phẩm trong danh sách yêu thích`
              : 'Danh sách yêu thích của bạn đang trống'}
          </p>
        </div>

        {/* Error State */}
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-6">
            <div className="flex items-center gap-3">
              <svg className="w-6 h-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <div>
                <p className="text-red-800 font-medium">{error}</p>
                <button
                  onClick={loadWishlist}
                  className="text-red-600 text-sm underline hover:text-red-700 mt-1"
                >
                  Thử lại
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Empty State */}
        {!error && wishlistItems.length === 0 && (
          <div className="bg-white rounded-lg shadow-sm p-12 text-center">
            <svg
              className="mx-auto h-24 w-24 text-gray-400 mb-4"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"
              />
            </svg>
            <h3 className="text-xl font-semibold text-gray-900 mb-2">
              Danh sách yêu thích trống
            </h3>
            <p className="text-gray-600 mb-6">
              Hãy khám phá và thêm những sản phẩm yêu thích vào danh sách của bạn
            </p>
            <Link
              href="/products"
              className="inline-block px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              Khám phá sản phẩm
            </Link>
          </div>
        )}

        {/* Wishlist Items */}
        {!error && wishlistItems.length > 0 && (
          <>
            {/* Clear All Button */}
            <div className="mb-4 flex justify-end">
              <button
                onClick={() => setShowClearModal(true)}
                className="px-4 py-2 text-red-600 border border-red-300 rounded-lg hover:bg-red-50 transition-colors"
              >
                Xóa tất cả
              </button>
            </div>

            {/* Items Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {wishlistItems.map((item) => (
                <div
                  key={item.id}
                  className="bg-white rounded-lg shadow-sm overflow-hidden hover:shadow-md transition-shadow"
                >
                  {/* Product Image */}
                  <Link href={`/products/${item.product.slug}`} className="block">
                    <div className="relative aspect-square bg-gray-100">
                      {item.product.images && item.product.images.length > 0 ? (
                        <img
                          src={
                            typeof item.product.images[0] === 'string'
                              ? item.product.images[0]
                              : item.product.images.find((img: any) => img.isPrimary)?.imageUrl || item.product.images[0]?.imageUrl
                          }
                          alt={item.product.name}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center text-gray-400">
                          <svg className="w-16 h-16" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                        </div>
                      )}
                      {item.product.discountPercentage && item.product.discountPercentage > 0 && (
                        <div className="absolute top-2 left-2 bg-red-600 text-white text-xs font-bold px-2 py-1 rounded">
                          -{item.product.discountPercentage}%
                        </div>
                      )}
                    </div>
                  </Link>

                  {/* Product Info */}
                  <div className="p-4">
                    <Link href={`/products/${item.product.slug}`}>
                      <h3 className="font-semibold text-gray-900 mb-2 line-clamp-2 hover:text-blue-600 transition-colors">
                        {item.product.name}
                      </h3>
                    </Link>

                    {/* Price */}
                    <div className="mb-3">
                      {item.product.discountPrice ? (
                        <div className="flex items-center gap-2">
                          <span className="text-lg font-bold text-red-600">
                            {item.product.discountPrice.toLocaleString('vi-VN')}đ
                          </span>
                          <span className="text-sm text-gray-500 line-through">
                            {item.product.price.toLocaleString('vi-VN')}đ
                          </span>
                        </div>
                      ) : (
                        <span className="text-lg font-bold text-gray-900">
                          {item.product.price.toLocaleString('vi-VN')}đ
                        </span>
                      )}
                    </div>

                    {/* Stock Status */}
                    <div className="mb-3">
                      {item.product.stockQuantity > 0 ? (
                        <span className="text-sm text-green-600">Còn hàng</span>
                      ) : (
                        <span className="text-sm text-red-600">Hết hàng</span>
                      )}
                    </div>

                    {/* Added Date */}
                    <p className="text-xs text-gray-500 mb-3">
                      Đã thêm: {new Date(item.addedAt).toLocaleDateString('vi-VN')}
                    </p>

                    {/* Action Buttons */}
                    <div className="flex gap-2">
                      <Link
                        href={`/products/${item.product.slug}`}
                        className="flex-1 px-4 py-2 bg-blue-600 text-white text-center text-sm rounded-lg hover:bg-blue-700 transition-colors"
                      >
                        Xem chi tiết
                      </Link>
                      <button
                        onClick={() => handleRemoveItem(item.id)}
                        disabled={removingId === item.id}
                        className="px-4 py-2 border border-red-300 text-red-600 text-sm rounded-lg hover:bg-red-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        title="Xóa khỏi danh sách yêu thích"
                      >
                        {removingId === item.id ? (
                          <svg className="animate-spin h-5 w-5" fill="none" viewBox="0 0 24 24">
                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                          </svg>
                        ) : (
                          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                        )}
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      {/* Clear Wishlist Modal */}
      {showClearModal && (
        <div className="fixed inset-0 bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg max-w-md w-full p-6">
            <div className="flex items-center mb-4">
              <div className="w-12 h-12 rounded-full bg-red-100 flex items-center justify-center mr-4">
                <svg className="w-6 h-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
              </div>
              <h3 className="text-xl font-bold text-gray-900">Xóa tất cả?</h3>
            </div>

            <p className="text-gray-600 mb-6">
              Bạn có chắc chắn muốn xóa tất cả {wishlistItems.length} sản phẩm khỏi danh sách yêu thích không?
              Hành động này không thể hoàn tác.
            </p>

            <div className="flex gap-4">
              <button
                onClick={handleClearWishlist}
                disabled={clearing}
                className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {clearing ? (
                  <span className="flex items-center justify-center">
                    <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Đang xóa...
                  </span>
                ) : (
                  'Xóa tất cả'
                )}
              </button>
              <button
                onClick={() => setShowClearModal(false)}
                disabled={clearing}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50 transition-colors"
              >
                Hủy
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
