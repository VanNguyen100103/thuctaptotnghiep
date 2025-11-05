'use client';

import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { useDebounce } from '@/hooks/useDebounce';
import WriteReviewModal from './WriteReviewModal';
import type { ReviewListResponse } from '@/types/review';

interface ReviewsSectionProps {
  reviews: ReviewListResponse;
  reviewStats?: {
    averageRating: number;
    totalReviews: number;
    ratingDistribution: { [key: string]: number };
  };
  productAverageRating?: number;
  productId: number;
  productName: string;
  onRefresh?: () => void;
}

export default function ReviewsSection({
  reviews: initialReviews,
  reviewStats,
  productAverageRating,
  productId,
  productName,
  onRefresh,
}: ReviewsSectionProps) {
  const { isAuthenticated } = useAuth();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedRatings, setSelectedRatings] = useState<number[]>([]);
  const [showWriteReview, setShowWriteReview] = useState(false);
  const [reviews, setReviews] = useState(initialReviews);
  const [loading, setLoading] = useState(false);

  // State for helpful/report actions
  const [helpfulReviews, setHelpfulReviews] = useState<Set<number>>(new Set());
  const [reportedReviews, setReportedReviews] = useState<Set<number>>(new Set());
  const [loadingActions, setLoadingActions] = useState<{ [key: number]: 'helpful' | 'report' | null }>({});
  const [showReportConfirm, setShowReportConfirm] = useState<number | null>(null);

  // Debounce search query (wait 500ms after user stops typing)
  const debouncedSearchQuery = useDebounce(searchQuery, 1000);

  // Fetch filtered reviews
  const fetchFilteredReviews = useCallback(async () => {
    setLoading(true);
    try {
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';

      // Build query parameters
      const params = new URLSearchParams();

      if (debouncedSearchQuery.trim()) {
        params.append('keyword', debouncedSearchQuery.trim());
      }

      if (selectedRatings.length > 0) {
        selectedRatings.forEach(rating => params.append('ratings', rating.toString()));
      }

      // Use filter endpoint if there are filters, otherwise use regular endpoint
      const endpoint = (debouncedSearchQuery.trim() || selectedRatings.length > 0)
        ? `/api/reviews/product/${productId}/filter?${params.toString()}`
        : `/api/reviews/product/${productId}`;

      const response = await fetch(`${baseUrl}${endpoint}`);

      if (!response.ok) {
        throw new Error('Failed to fetch reviews');
      }

      const data = await response.json();
      setReviews(data);
    } catch (error) {
      console.error('Error fetching filtered reviews:', error);
    } finally {
      setLoading(false);
    }
  }, [debouncedSearchQuery, selectedRatings, productId]);

  // Trigger fetch when debounced search or ratings change
  useEffect(() => {
    fetchFilteredReviews();
  }, [fetchFilteredReviews]);

  // Update reviews when initial reviews change (e.g., after creating a new review)
  useEffect(() => {
    setReviews(initialReviews);
  }, [initialReviews]);

  // Handler for marking review as helpful
  const handleMarkHelpful = async (reviewId: number) => {
    if (!isAuthenticated) {
      alert('Vui lòng đăng nhập để đánh dấu hữu ích');
      return;
    }

    if (helpfulReviews.has(reviewId)) {
      return; // Already marked as helpful
    }

    setLoadingActions(prev => ({ ...prev, [reviewId]: 'helpful' }));

    try {
      const token = localStorage.getItem('accessToken');
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';

      const response = await fetch(`${baseUrl}/api/reviews/${reviewId}/helpful`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        credentials: 'include',
      });

      if (!response.ok) {
        throw new Error('Failed to mark as helpful');
      }

      // Mark as helpful locally
      setHelpfulReviews(prev => new Set(prev).add(reviewId));

      // Refresh reviews to get updated count
      onRefresh?.();
    } catch (error) {
      console.error('Error marking review as helpful:', error);
      alert('Có lỗi xảy ra. Vui lòng thử lại.');
    } finally {
      setLoadingActions(prev => ({ ...prev, [reviewId]: null }));
    }
  };

  // Handler for reporting review
  const handleReportReview = async (reviewId: number) => {
    if (!isAuthenticated) {
      alert('Vui lòng đăng nhập để báo cáo đánh giá');
      return;
    }

    if (reportedReviews.has(reviewId)) {
      return; // Already reported
    }

    setLoadingActions(prev => ({ ...prev, [reviewId]: 'report' }));

    try {
      const token = localStorage.getItem('accessToken');
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';

      const response = await fetch(`${baseUrl}/api/reviews/${reviewId}/report`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        credentials: 'include',
      });

      if (!response.ok) {
        throw new Error('Failed to report review');
      }

      // Mark as reported locally
      setReportedReviews(prev => new Set(prev).add(reviewId));
      setShowReportConfirm(null);

      alert('Cảm ơn bạn đã báo cáo. Chúng tôi sẽ xem xét đánh giá này.');
    } catch (error) {
      console.error('Error reporting review:', error);
      alert('Có lỗi xảy ra. Vui lòng thử lại.');
    } finally {
      setLoadingActions(prev => ({ ...prev, [reviewId]: null }));
    }
  };

  const averageRating = reviews.averageRating || reviewStats?.averageRating || productAverageRating || 0;
  const totalReviews = reviews.totalElements || reviewStats?.totalReviews || 0;

  return (
    <section className="mb-16 bg-gray-50 -mx-4 sm:-mx-6 lg:-mx-8 px-4 sm:px-6 lg:px-8 py-12">
      {/* Write Review Modal */}
      <WriteReviewModal
        isOpen={showWriteReview}
        onClose={() => setShowWriteReview(false)}
        productId={productId}
        productName={productName}
        onReviewCreated={() => {
          onRefresh?.();
        }}
      />

      {/* Header with Write Review Button */}
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-3xl font-bold text-gray-900">
          ĐÁNH GIÁ<br />SẢN PHẨM
        </h2>
        {isAuthenticated ? (
          <button
            onClick={() => setShowWriteReview(true)}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-lg font-semibold transition-colors"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
            Viết đánh giá
          </button>
        ) : (
          <a
            href="/login"
            className="flex items-center gap-2 bg-gray-600 hover:bg-gray-700 text-white px-6 py-3 rounded-lg font-semibold transition-colors"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1" />
            </svg>
            Đăng nhập để đánh giá
          </a>
        )}
      </div>

      {/* Rating Summary */}
      <div className="bg-white rounded-lg p-8 mb-6">
        <div className="flex flex-col md:flex-row gap-12">
          {/* Left: Overall Rating */}
          <div className="flex-shrink-0 py-1">
            <div className="text-7xl font-bold text-gray-900 mb-3">
              {averageRating.toFixed(1)}
            </div>
            <div className="flex items-center gap-1 mb-2">
              {[1, 2, 3, 4, 5].map((star) => (
                <svg
                  key={star}
                  className={`w-8 h-8 ${
                    star <= Math.floor(averageRating) ? 'text-yellow-400' : 'text-gray-300'
                  }`}
                  fill="currentColor"
                  viewBox="0 0 20 20"
                >
                  <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                </svg>
              ))}
            </div>
            <p className="text-gray-600 text-sm">
              Dựa trên {totalReviews} đánh giá đến từ khách hàng
            </p>
          </div>

          {/* Right: Fit Metrics */}
          <div className="flex-1">
            <h3 className="font-bold text-gray-900 mb-4 text-lg">Phù hợp với cơ thể</h3>
            <div className="space-y-4">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-gray-600">Chặt</span>
                  <span className="text-sm font-semibold text-gray-900">0%</span>
                </div>
                <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                  <div className="h-full bg-gray-300" style={{ width: '0%' }} />
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-gray-600">Đúng kích thước</span>
                  <span className="text-sm font-semibold text-gray-900">100%</span>
                </div>
                <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                  <div className="h-full bg-black" style={{ width: '100%' }} />
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-gray-600">Rộng</span>
                  <span className="text-sm font-semibold text-gray-900">0%</span>
                </div>
                <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                  <div className="h-full bg-gray-300" style={{ width: '0%' }} />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Reviews Grid - Sidebar + Content */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Left Sidebar - Filters */}
        <div className="lg:col-span-1">
          <div className="bg-white rounded-lg p-6 sticky top-4">
            {/* Search Box */}
            <div className="mb-6">
              <h3 className="text-sm font-bold text-gray-900 mb-3">Lọc đánh giá</h3>
              <div className="relative">
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Tìm kiếm đánh giá"
                  className="w-full px-4 py-2 pl-10 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <svg
                  className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
              </div>
            </div>

            {/* Star Rating Filters */}
            <div className="mb-6">
              <h3 className="text-sm font-bold text-gray-900 mb-3">Phân loại xếp hạng</h3>
              <div className="space-y-2">
                {[5, 4, 3, 2, 1].map((rating) => (
                  <label key={rating} className="flex items-center gap-2 cursor-pointer hover:bg-gray-50 p-1 rounded">
                    <input
                      type="checkbox"
                      checked={selectedRatings.includes(rating)}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setSelectedRatings([...selectedRatings, rating]);
                        } else {
                          setSelectedRatings(selectedRatings.filter(r => r !== rating));
                        }
                      }}
                      className="w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                    />
                    <div className="flex items-center gap-1">
                      <span className="text-sm font-medium text-gray-900">{rating}</span>
                      {[...Array(rating)].map((_, i) => (
                        <svg key={i} className="w-4 h-4 text-yellow-400" fill="currentColor" viewBox="0 0 20 20">
                          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                        </svg>
                      ))}
                      {[...Array(5 - rating)].map((_, i) => (
                        <svg key={`empty-${i}`} className="w-4 h-4 text-gray-300" fill="currentColor" viewBox="0 0 20 20">
                          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                        </svg>
                      ))}
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {/* Additional Filter */}
            <div>
              <h3 className="text-sm font-bold text-gray-900 mb-3">Lọc phản hồi</h3>
              <label className="flex items-start gap-2 cursor-pointer hover:bg-gray-50 p-2 rounded">
                <input type="checkbox" className="mt-0.5 w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
                <span className="text-xs text-gray-600 leading-relaxed">
                  Các review đều đến từ khách hàng đã thực sự mua hàng của Coolmate
                </span>
              </label>
            </div>
          </div>
        </div>

        {/* Right Content - Reviews List */}
        <div className="lg:col-span-3">
          {/* Top Bar */}
          <div className="flex items-center justify-between mb-6">
            <p className="text-sm font-medium text-gray-900">
              Hiển thị đánh giá 1-10
            </p>
            <select className="px-4 py-2 bg-white border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
              <option>Sắp xếp</option>
              <option>Mới nhất</option>
              <option>Cũ nhất</option>
              <option>Đánh giá cao nhất</option>
              <option>Đánh giá thấp nhất</option>
            </select>
          </div>

          {/* Reviews List */}
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
            </div>
          ) : reviews.content && reviews.content.length > 0 ? (
            <div className="space-y-4">
              {reviews.content.map((review) => (
                <div key={review.id} className="bg-white rounded-lg p-6 border border-gray-200">
                  <div className="flex items-start gap-4">
                    {/* User Avatar */}
                    <div className="w-12 h-12 bg-gray-200 rounded-full flex items-center justify-center flex-shrink-0">
                      <span className="text-lg font-semibold text-gray-600">
                        {review.username?.charAt(0).toUpperCase() || 'U'}
                      </span>
                    </div>

                    {/* Review Content */}
                    <div className="flex-1">
                      <div className="flex items-center justify-between mb-2">
                        <div>
                          <p className="font-semibold text-gray-900">{review.username || 'Anonymous'}</p>
                          {review.verified && (
                            <span className="text-xs text-green-600 flex items-center gap-1 mt-1">
                              <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                              </svg>
                              Đã mua hàng
                            </span>
                          )}
                        </div>
                        <span className="text-sm text-gray-500">
                          {new Date(review.createdAt).toLocaleDateString('vi-VN')}
                        </span>
                      </div>

                      {/* Rating Stars */}
                      <div className="flex items-center gap-1 mb-3">
                        {[1, 2, 3, 4, 5].map((star) => (
                          <svg
                            key={star}
                            className={`w-4 h-4 ${star <= review.rating ? 'text-yellow-400' : 'text-gray-300'}`}
                            fill="currentColor"
                            viewBox="0 0 20 20"
                          >
                            <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                          </svg>
                        ))}
                      </div>

                      {/* Review Comment */}
                      <p className="text-gray-700 mb-3 leading-relaxed">
                        {review.comment}
                      </p>

                      {/* Review Images */}
                      {review.images && review.images.length > 0 && (
                        <div className="flex gap-2 mb-3 flex-wrap">
                          {review.images.map((img: any) => (
                            <img
                              key={img.id}
                              src={img.imageUrl}
                              alt="Review"
                              className="w-20 h-20 object-cover rounded-lg border border-gray-200"
                            />
                          ))}
                        </div>
                      )}

                      {/* Action Buttons */}
                      <div className="flex items-center gap-4">
                        {/* Helpful Button */}
                        <button
                          onClick={() => handleMarkHelpful(review.id)}
                          disabled={helpfulReviews.has(review.id) || loadingActions[review.id] === 'helpful'}
                          className={`flex items-center gap-1 text-sm transition-colors ${
                            helpfulReviews.has(review.id)
                              ? 'text-blue-600 font-semibold cursor-default'
                              : 'text-gray-600 hover:text-blue-600'
                          } ${loadingActions[review.id] === 'helpful' ? 'opacity-50 cursor-wait' : ''}`}
                        >
                          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5" />
                          </svg>
                          {helpfulReviews.has(review.id) ? 'Đã đánh dấu hữu ích' : 'Hữu ích'} {review.helpfulCount > 0 && `(${review.helpfulCount})`}
                        </button>

                        {/* Report Button */}
                        {showReportConfirm === review.id ? (
                          <div className="flex items-center gap-2">
                            <span className="text-xs text-gray-600">Báo cáo đánh giá này?</span>
                            <button
                              onClick={() => handleReportReview(review.id)}
                              disabled={loadingActions[review.id] === 'report'}
                              className="text-xs text-red-600 hover:text-red-700 font-semibold"
                            >
                              {loadingActions[review.id] === 'report' ? 'Đang xử lý...' : 'Xác nhận'}
                            </button>
                            <button
                              onClick={() => setShowReportConfirm(null)}
                              className="text-xs text-gray-600 hover:text-gray-700"
                            >
                              Hủy
                            </button>
                          </div>
                        ) : (
                          <button
                            onClick={() => {
                              if (!isAuthenticated) {
                                alert('Vui lòng đăng nhập để báo cáo đánh giá');
                                return;
                              }
                              setShowReportConfirm(review.id);
                            }}
                            disabled={reportedReviews.has(review.id)}
                            className={`flex items-center gap-1 text-sm transition-colors ${
                              reportedReviews.has(review.id)
                                ? 'text-gray-400 cursor-not-allowed'
                                : 'text-gray-600 hover:text-red-600'
                            }`}
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 21v-4m0 0V5a2 2 0 012-2h6.5l1 1H21l-3 6 3 6h-8.5l-1-1H5a2 2 0 00-2 2zm9-13.5V9" />
                            </svg>
                            {reportedReviews.has(review.id) ? 'Đã báo cáo' : 'Báo cáo'}
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="bg-white rounded-lg p-12 text-center">
              <svg className="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
              </svg>
              <p className="text-gray-600 text-lg">
                Chưa có đánh giá nào. Hãy là người đầu tiên đánh giá sản phẩm này!
              </p>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
