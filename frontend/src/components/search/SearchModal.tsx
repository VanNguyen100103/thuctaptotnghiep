/**
 * AI Search Modal Component
 * - AI Smart Search (authenticated only)
 */

'use client';

import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { cleanAIText } from '@/utils/textUtils';
import Link from 'next/link';

interface ProductImage {
  id: number;
  imageUrl: string;
  isPrimary: boolean;
  displayOrder: number;
}

interface Product {
  id: number;
  name: string;
  slug: string;
  price: number;
  salePrice: number | null;
  compareAtPrice?: number;
  images: ProductImage[];
  averageRating: number;
  totalReviews: number;
}

interface AISearchResponse {
  products: Product[];
  aiExplanation: string;
  prompt: string;
}

interface SearchModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function SearchModal({ isOpen, onClose }: SearchModalProps) {
  const { isAuthenticated } = useAuth();
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // AI search results
  const [aiResults, setAiResults] = useState<Product[]>([]);
  const [aiExplanation, setAiExplanation] = useState('');

  // Reset when modal opens/closes
  useEffect(() => {
    if (!isOpen) {
      setSearchQuery('');
      setAiResults([]);
      setAiExplanation('');
      setError(null);
    }
  }, [isOpen]);

  const handleAISearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;

    if (!isAuthenticated) {
      setError('Vui lòng đăng nhập để sử dụng tìm kiếm AI');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      setAiResults([]);
      setAiExplanation('');

      const token = localStorage.getItem('accessToken');
      if (!token) {
        setError('Vui lòng đăng nhập để sử dụng tính năng này');
        return;
      }

      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';
      const response = await fetch(
        `${baseUrl}/api/ai/smart-search?q=${encodeURIComponent(searchQuery)}`,
        {
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
          },
          credentials: 'include',
        }
      );

      if (!response.ok) {
        throw new Error('Không thể tìm kiếm với AI');
      }

      const data: AISearchResponse = await response.json();
      console.log('[AISearch] Response data:', data);

      setAiResults(data.products);
      setAiExplanation(data.aiExplanation || '');
    } catch (err) {
      console.error('Error with AI search:', err);
      setError('Không thể tìm kiếm với AI. Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  // Show login prompt if not authenticated
  if (!isAuthenticated) {
    return (
      <>
        {/* Backdrop */}
        <div
          className="fixed inset-0 bg-black/50 z-50"
          onClick={onClose}
        />

        {/* Modal */}
        <div className="fixed inset-x-0 top-0 z-50 max-w-4xl mx-auto mt-20">
          <div className="bg-white rounded-xl shadow-2xl mx-4">
            {/* Header */}
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
                <span>✨</span>
                Tìm kiếm với AI
              </h2>
              <button
                onClick={onClose}
                className="p-2 hover:bg-gray-100 rounded-full transition-colors"
              >
                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Login Required Message */}
            <div className="p-12 text-center">
              <div className="text-6xl mb-4">🔒</div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">
                Đăng nhập để sử dụng tìm kiếm AI
              </h3>
              <p className="text-sm text-gray-600 mb-6">
                Tính năng tìm kiếm thông minh với AI chỉ dành cho thành viên
              </p>
              <Link
                href="/login"
                className="inline-block bg-purple-600 hover:bg-purple-700 text-white px-6 py-3 rounded-lg font-semibold transition-colors"
                onClick={onClose}
              >
                Đăng nhập ngay
              </Link>
            </div>
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/50 z-50"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="fixed inset-x-0 top-0 z-50 max-w-4xl mx-auto mt-20">
        <div className="bg-white rounded-xl shadow-2xl mx-4">
          {/* Header */}
          <div className="flex items-center justify-between p-6 border-b border-gray-200">
            <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
              <span>✨</span>
              Tìm kiếm với AI
            </h2>
            <button
              onClick={onClose}
              className="p-2 hover:bg-gray-100 rounded-full transition-colors"
            >
              <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Search Form */}
          <div className="p-6">
            <form onSubmit={handleAISearch} className="relative">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Ví dụ: áo thun nam, váy nữ mùa hè..."
                className="w-full px-4 py-3 pl-12 pr-24 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                autoFocus
              />
              <svg
                className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <button
                type="submit"
                disabled={loading || !searchQuery.trim()}
                className="absolute right-2 top-1/2 -translate-y-1/2 px-4 py-2 bg-purple-600 hover:bg-purple-700 disabled:bg-gray-300 text-white rounded-lg font-semibold transition-colors"
              >
                {loading ? 'Đang tìm...' : 'Tìm kiếm'}
              </button>
            </form>

            {/* Error Message */}
            {error && (
              <div className="mt-4 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
                {error}
              </div>
            )}
          </div>

          {/* Results */}
          <div className="max-h-[60vh] overflow-y-auto px-6 pb-6">
            {/* Loading State */}
            {loading && (
              <div className="flex items-center justify-center py-12">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600"></div>
              </div>
            )}

            {/* AI Search Results */}
            {!loading && aiResults.length > 0 && (
              <div>
                {/* AI Explanation Box */}
                {aiExplanation && (
                  <div className="mb-6 bg-gradient-to-r from-purple-50 to-pink-50 rounded-xl p-5 border-2 border-purple-200">
                    <div className="flex items-start gap-3">
                      <div className="flex-shrink-0 w-10 h-10 bg-gradient-to-br from-purple-500 to-pink-500 rounded-full flex items-center justify-center">
                        <span className="text-xl">🤖</span>
                      </div>
                      <div className="flex-1">
                        <h4 className="text-sm font-bold text-gray-900 mb-2 flex items-center gap-2">
                          <span>✨</span>
                          Phân tích từ AI
                        </h4>
                        <div className="text-sm text-gray-700 leading-relaxed whitespace-pre-line">
                          {cleanAIText(aiExplanation)}
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Products Grid */}
                <div className="mb-4 text-sm text-gray-600">
                  Tìm thấy <strong>{aiResults.length}</strong> sản phẩm phù hợp
                </div>
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                  {aiResults.map((product) => {
                    // Get primary image or first image
                    const primaryImage = product.images?.find(img => img.isPrimary) || product.images?.[0];
                    const imageUrl = primaryImage?.imageUrl || '/placeholder-product.png';

                    return (
                      <Link
                        key={product.id}
                        href={`/product/${product.slug}`}
                        onClick={onClose}
                        className="group bg-white border border-gray-200 rounded-lg hover:shadow-lg transition-all overflow-hidden"
                      >
                        <div className="aspect-square overflow-hidden bg-gray-100">
                          <img
                            src={imageUrl}
                            alt={product.name}
                            className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                          />
                        </div>
                        <div className="p-3">
                          <h3 className="text-sm font-semibold text-gray-900 line-clamp-2 mb-2">
                            {product.name}
                          </h3>
                          <div className="flex items-center gap-2">
                            {product.salePrice ? (
                              <>
                                <span className="text-base font-bold text-red-600">
                                  {product.salePrice.toLocaleString('vi-VN')}₫
                                </span>
                                <span className="text-xs text-gray-400 line-through">
                                  {product.compareAtPrice?.toLocaleString('vi-VN') || product.price.toLocaleString('vi-VN')}₫
                                </span>
                              </>
                            ) : (
                              <span className="text-base font-bold text-gray-900">
                                {product.price.toLocaleString('vi-VN')}₫
                              </span>
                            )}
                          </div>
                        </div>
                      </Link>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Empty State */}
            {!loading && aiResults.length === 0 && searchQuery && (
              <div className="py-12 text-center">
                <div className="text-6xl mb-4">🔍</div>
                <h3 className="text-lg font-semibold text-gray-900 mb-2">
                  Không tìm thấy sản phẩm
                </h3>
                <p className="text-sm text-gray-600">
                  Thử tìm kiếm với từ khóa khác
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  );
}
