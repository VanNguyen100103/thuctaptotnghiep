'use client';

/**
 * Trang Chi Tiết Đánh Giá (Admin)
 * Hiển thị thông tin đầy đủ của một review
 */

import { useParams, useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';

interface ReviewImage {
  id: number;
  imageUrl: string;
  displayOrder: number;
}

interface ReviewDetail {
  id: number;
  productId: number;
  productName: string;
  userId: number;
  username: string;
  userAvatarUrl: string | null;
  rating: number;
  title: string;
  comment: string;
  verified: boolean;
  approved: boolean;
  helpfulCount: number;
  reportCount: number;
  createdAt: string;
  updatedAt: string;
  images: ReviewImage[];
}

export default function ReviewDetailPage() {
  const params = useParams();
  const router = useRouter();
  const reviewId = params.id as string;

  // Fetch review detail
  const { data: review, isLoading } = useQuery<ReviewDetail>({
    queryKey: ['admin-review-detail', reviewId],
    queryFn: async () => {
      const token = localStorage.getItem('accessToken');
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';
      const response = await fetch(`${baseUrl}/api/reviews/${reviewId}`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        credentials: 'include',
      });

      if (!response.ok) throw new Error('Failed to fetch review');
      return response.json();
    },
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (!review) {
    return (
      <div className="p-6">
        <div className="bg-red-50 text-red-600 p-4 rounded-lg">
          Không tìm thấy đánh giá
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 max-w-4xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={() => router.back()}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-2xl font-bold text-gray-900">Chi Tiết Đánh Giá #{review.id}</h1>
        </div>

        {/* Status Badge */}
        {review.approved ? (
          <span className="px-3 py-1 text-sm font-semibold rounded-full bg-green-100 text-green-800">
            Đã duyệt
          </span>
        ) : (
          <span className="px-3 py-1 text-sm font-semibold rounded-full bg-yellow-100 text-yellow-800">
            Chờ duyệt
          </span>
        )}
      </div>

      {/* Main Content */}
      <div className="bg-white rounded-lg shadow-md p-6 space-y-6">
        {/* Product Info */}
        <div className="pb-6 border-b border-gray-200">
          <h2 className="text-sm font-semibold text-gray-500 uppercase mb-2">Sản Phẩm</h2>
          <Link
            href={`/admin/products/${review.productId}`}
            className="text-lg text-blue-600 hover:text-blue-800 hover:underline font-medium"
          >
            {review.productName}
          </Link>
        </div>

        {/* User Info */}
        <div className="pb-6 border-b border-gray-200">
          <h2 className="text-sm font-semibold text-gray-500 uppercase mb-3">Người Đánh Giá</h2>
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 bg-gray-200 rounded-full flex items-center justify-center">
              {review.userAvatarUrl ? (
                <img
                  src={review.userAvatarUrl}
                  alt={review.username}
                  className="w-16 h-16 rounded-full object-cover"
                />
              ) : (
                <span className="text-2xl font-semibold text-gray-600">
                  {review.username?.charAt(0).toUpperCase() || 'U'}
                </span>
              )}
            </div>
            <div>
              <div className="font-medium text-gray-900">{review.username}</div>
              <div className="text-sm text-gray-500">User ID: {review.userId}</div>
              {review.verified && (
                <div className="flex items-center gap-1 mt-1">
                  <svg className="w-4 h-4 text-green-600" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                  </svg>
                  <span className="text-sm text-green-600 font-medium">Đã mua sản phẩm</span>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Rating */}
        <div className="pb-6 border-b border-gray-200">
          <h2 className="text-sm font-semibold text-gray-500 uppercase mb-3">Đánh Giá</h2>
          <div className="flex items-center gap-2">
            {[...Array(5)].map((_, i) => (
              <svg
                key={i}
                className={`w-8 h-8 ${
                  i < review.rating ? 'text-yellow-400' : 'text-gray-300'
                }`}
                fill="currentColor"
                viewBox="0 0 20 20"
              >
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
            ))}
            <span className="ml-2 text-2xl font-bold text-gray-900">{review.rating}/5</span>
          </div>
        </div>

        {/* Review Content */}
        <div className="pb-6 border-b border-gray-200">
          <h2 className="text-sm font-semibold text-gray-500 uppercase mb-3">Nội Dung</h2>
          <div className="space-y-3">
            <div>
              <h3 className="text-xl font-bold text-gray-900">{review.title}</h3>
            </div>
            <div>
              <p className="text-gray-700 whitespace-pre-wrap leading-relaxed">{review.comment}</p>
            </div>
          </div>
        </div>

        {/* Images */}
        {review.images && review.images.length > 0 && (
          <div className="pb-6 border-b border-gray-200">
            <h2 className="text-sm font-semibold text-gray-500 uppercase mb-3">Hình Ảnh ({review.images.length})</h2>
            <div className="grid grid-cols-4 gap-4">
              {review.images.map((img) => (
                <img
                  key={img.id}
                  src={img.imageUrl}
                  alt="Review"
                  className="w-full h-32 object-cover rounded-lg border border-gray-200 hover:scale-105 transition-transform cursor-pointer"
                  onClick={() => window.open(img.imageUrl, '_blank')}
                />
              ))}
            </div>
          </div>
        )}

        {/* Statistics */}
        <div className="pb-6 border-b border-gray-200">
          <h2 className="text-sm font-semibold text-gray-500 uppercase mb-3">Thống Kê</h2>
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-blue-50 rounded-lg p-4">
              <div className="flex items-center gap-2 mb-1">
                <svg className="w-5 h-5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5" />
                </svg>
                <span className="text-sm font-medium text-blue-900">Hữu ích</span>
              </div>
              <div className="text-2xl font-bold text-blue-600">{review.helpfulCount}</div>
            </div>

            <div className="bg-red-50 rounded-lg p-4">
              <div className="flex items-center gap-2 mb-1">
                <svg className="w-5 h-5 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 21v-4m0 0V5a2 2 0 012-2h6.5l1 1H21l-3 6 3 6h-8.5l-1-1H5a2 2 0 00-2 2zm9-13.5V9" />
                </svg>
                <span className="text-sm font-medium text-red-900">Báo cáo</span>
              </div>
              <div className="text-2xl font-bold text-red-600">{review.reportCount}</div>
            </div>
          </div>
        </div>

        {/* Timestamps */}
        <div>
          <h2 className="text-sm font-semibold text-gray-500 uppercase mb-3">Thời Gian</h2>
          <div className="space-y-2 text-sm text-gray-600">
            <div className="flex items-center gap-2">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>Tạo: {new Date(review.createdAt).toLocaleString('vi-VN')}</span>
            </div>
            <div className="flex items-center gap-2">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>Cập nhật: {new Date(review.updatedAt).toLocaleString('vi-VN')}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Back Button */}
      <div className="mt-6 flex justify-end">
        <button
          onClick={() => router.back()}
          className="px-6 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors font-medium"
        >
          Quay lại
        </button>
      </div>
    </div>
  );
}
