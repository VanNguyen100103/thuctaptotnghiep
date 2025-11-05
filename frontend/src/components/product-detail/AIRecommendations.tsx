/**
 * AI Product Recommendations Component
 * Shows AI-powered product recommendations with explanations
 * REQUIRES AUTHENTICATION
 */

'use client';

import { useEffect, useState, useRef } from 'react';
import Link from 'next/link';
import { useAuth } from '@/contexts/AuthContext';

interface RecommendedProduct {
  id: number;
  name: string;
  slug: string;
  price: number;
  compareAtPrice?: number;
  images: Array<{
    imageUrl: string;
    isPrimary: boolean;
  }>;
  averageRating?: number;
  reviewCount?: number;
  inStock: boolean;
  discountPercentage?: number;
  aiExplanation: string; // AI explanation for why this product is recommended
  prompt: string; // AI prompt used
}

interface AIRecommendationsProps {
  productId: number;
}

export default function AIRecommendations({ productId }: AIRecommendationsProps) {
  const { isAuthenticated } = useAuth();
  const [recommendations, setRecommendations] = useState<RecommendedProduct[]>([]);
  const [aiExplanation, setAiExplanation] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Only fetch if user is authenticated
    if (!isAuthenticated) {
      setLoading(false);
      return;
    }

    const fetchRecommendations = async () => {
      try {
        setLoading(true);
        setError(null);

        // Get auth token
        const token = localStorage.getItem('accessToken');

        const headers: Record<string, string> = {
          'Content-Type': 'application/json',
        };

        if (token) {
          headers['Authorization'] = `Bearer ${token}`;
        }

        // Use API base URL from environment or relative path
        const baseUrl = process.env.NEXT_PUBLIC_API_URL || '';
        const response = await fetch(`${baseUrl}/api/ai/recommendations?productId=${productId}`, {
          headers,
          credentials: 'include',
        });

        if (!response.ok) {
          throw new Error('Failed to fetch recommendations');
        }

        const data = await response.json();
        console.log('[AIRecommendations] Response data:', data);

        // Handle different response structures
        if (Array.isArray(data)) {
          setRecommendations(data);
        } else if (data.products && Array.isArray(data.products)) {
          setRecommendations(data.products);
          // Extract the general AI explanation
          if (data.aiExplanation) {
            setAiExplanation(data.aiExplanation);
          }
        } else {
          console.error('[AIRecommendations] Unexpected response format:', data);
          setRecommendations([]);
        }
      } catch (err) {
        console.error('Error fetching AI recommendations:', err);
        setError('Không thể tải gợi ý sản phẩm');
      } finally {
        setLoading(false);
      }
    };

    fetchRecommendations();
  }, [productId, isAuthenticated]);

  const scroll = (direction: 'left' | 'right') => {
    if (scrollRef.current) {
      const scrollAmount = 300;
      scrollRef.current.scrollBy({
        left: direction === 'left' ? -scrollAmount : scrollAmount,
        behavior: 'smooth',
      });
    }
  };

  // Don't show if user is not authenticated
  if (!isAuthenticated) {
    return null;
  }

  if (loading) {
    return (
      <div className="py-12">
        <h2 className="text-2xl font-bold mb-6">🤖 AI Gợi ý cho bạn</h2>
        <div className="flex items-center justify-center h-32">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
        </div>
      </div>
    );
  }

  if (error || recommendations.length === 0) {
    return null; // Don't show section if there's an error or no recommendations
  }

  return (
    <div className="py-12 bg-gradient-to-r from-blue-50 to-purple-50 rounded-2xl px-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            🤖 AI Gợi ý cho bạn
          </h2>
          <p className="text-sm text-gray-600 mt-1">
            Dựa trên sở thích và lịch sử mua hàng của bạn
          </p>
        </div>
      </div>

      {/* AI Explanation - General explanation for all recommendations */}
      {aiExplanation && (
        <div className="mb-6 bg-white rounded-xl p-4 border-2 border-blue-200 shadow-sm">
          <div className="flex items-start gap-3">
            <span className="text-2xl flex-shrink-0">💡</span>
            <div>
              <h3 className="font-semibold text-gray-900 mb-2">Lý do AI gợi ý:</h3>
              <p className="text-sm text-gray-700 leading-relaxed">
                {aiExplanation}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Products Carousel */}
      <div className="relative">
        {/* Left Arrow */}
        <button
          onClick={() => scroll('left')}
          className="absolute left-0 top-1/2 -translate-y-1/2 z-10 w-10 h-10 bg-white rounded-full shadow-lg flex items-center justify-center hover:bg-gray-100 transition-colors"
        >
          <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>

        {/* Right Arrow */}
        <button
          onClick={() => scroll('right')}
          className="absolute right-0 top-1/2 -translate-y-1/2 z-10 w-10 h-10 bg-white rounded-full shadow-lg flex items-center justify-center hover:bg-gray-100 transition-colors"
        >
          <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
        </button>

        {/* Products Scroll Container */}
        <div
          ref={scrollRef}
          className="flex gap-6 overflow-x-auto scroll-smooth hide-scrollbar px-12"
          style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
        >
          {recommendations.map((product) => {
            const primaryImage = product.images?.find(img => img.isPrimary)?.imageUrl ||
                                product.images?.[0]?.imageUrl ||
                                'https://placehold.co/400x400?text=No+Image';

            const discount = product.discountPercentage ||
                           (product.compareAtPrice && product.compareAtPrice > product.price
                             ? Math.round(((product.compareAtPrice - product.price) / product.compareAtPrice) * 100)
                             : 0);

            return (
              <div
                key={product.id}
                className="group flex-shrink-0 w-72 bg-white rounded-xl overflow-hidden shadow-sm hover:shadow-2xl transition-all duration-300"
              >
                <Link href={`/product/${product.slug}`}>
                  {/* Product Image */}
                  <div className="relative aspect-square overflow-hidden bg-gray-100">
                    {discount > 0 && (
                      <div className="absolute top-3 left-3 z-10 bg-red-600 text-white px-3 py-1 rounded-md text-sm font-bold">
                        -{discount}%
                      </div>
                    )}

                    {/* AI Badge */}
                    <div className="absolute top-3 right-3 z-10 bg-gradient-to-r from-blue-600 to-purple-600 text-white px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1">
                      🤖 AI
                    </div>

                    <img
                      src={primaryImage}
                      alt={product.name}
                      className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300"
                    />

                    {!product.inStock && (
                      <div className="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center">
                        <span className="bg-red-600 text-white px-4 py-2 rounded-lg font-bold">
                          Hết hàng
                        </span>
                      </div>
                    )}
                  </div>

                  {/* Product Info */}
                  <div className="p-4">
                    {/* Product Name */}
                    <h3 className="font-medium text-gray-900 line-clamp-2 mb-2 min-h-[48px]">
                      {product.name}
                    </h3>

                    {/* Rating */}
                    {product.averageRating && product.reviewCount && (
                      <div className="flex items-center gap-2 mb-2">
                        <div className="flex items-center">
                          {[...Array(5)].map((_, i) => (
                            <svg
                              key={i}
                              className={`w-4 h-4 ${
                                i < Math.floor(product.averageRating!)
                                  ? 'text-yellow-400 fill-current'
                                  : 'text-gray-300'
                              }`}
                              viewBox="0 0 20 20"
                            >
                              <path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z" />
                            </svg>
                          ))}
                        </div>
                        <span className="text-sm text-gray-500">({product.reviewCount})</span>
                      </div>
                    )}

                    {/* Price */}
                    <div className="flex items-baseline gap-2 mb-3">
                      <span className="text-xl font-bold text-blue-600">
                        {product.price.toLocaleString('vi-VN')} ₫
                      </span>
                      {product.compareAtPrice && product.compareAtPrice > product.price && (
                        <span className="text-sm text-gray-400 line-through">
                          {product.compareAtPrice.toLocaleString('vi-VN')} ₫
                        </span>
                      )}
                    </div>

                    {/* AI Explanation */}
                    <div className="bg-gradient-to-r from-blue-50 to-purple-50 rounded-lg p-3 border border-blue-100">
                      <div className="flex items-start gap-2">
                        <span className="text-lg flex-shrink-0">💡</span>
                        <p className="text-sm text-gray-700 line-clamp-3">
                          {product.aiExplanation}
                        </p>
                      </div>
                    </div>
                  </div>
                </Link>
              </div>
            );
          })}
        </div>
      </div>

      {/* CSS to hide scrollbar */}
      <style jsx>{`
        .hide-scrollbar::-webkit-scrollbar {
          display: none;
        }
      `}</style>
    </div>
  );
}
