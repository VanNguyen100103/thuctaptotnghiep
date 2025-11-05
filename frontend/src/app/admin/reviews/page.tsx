'use client';

/**
 * Trang Quản Lý Đánh Giá
 * Hiển thị danh sách đánh giá với chức năng approve/reject/delete
 */

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';

interface Review {
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
  images: Array<{
    id: number;
    imageUrl: string;
    displayOrder: number;
  }>;
}

interface ReviewsResponse {
  content: Review[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

interface SentimentAnalysis {
  data: {
    productId: number;
    averageRating: number;
    totalReviews: number;
    sentiment: string;
    summary?: string;
    positivePoints?: string[];
    negativePoints?: string[];
  };
  aiExplanation: string;
  prompt: string;
}

export default function ReviewsManagementPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [sentimentData, setSentimentData] = useState<{ [key: number]: SentimentAnalysis }>({});
  const [showSentiment, setShowSentiment] = useState<number | null>(null);
  const [loadingSentiment, setLoadingSentiment] = useState<number | null>(null);

  // Fetch danh sách reviews
  const { data, isLoading } = useQuery<ReviewsResponse>({
    queryKey: ['admin-reviews', page],
    queryFn: async () => {
      const token = localStorage.getItem('accessToken');
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';
      const response = await fetch(`${baseUrl}/api/reviews/admin/all?page=${page}&size=20`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        credentials: 'include',
      });

      if (!response.ok) throw new Error('Failed to fetch reviews');
      return response.json();
    },
    refetchOnMount: 'always',
  });

  // Mutation approve review
  const approveMutation = useMutation({
    mutationFn: async (reviewId: number) => {
      const token = localStorage.getItem('accessToken');
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';
      const response = await fetch(`${baseUrl}/api/reviews/admin/${reviewId}/approve`, {
        method: 'PATCH',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        credentials: 'include',
      });

      if (!response.ok) throw new Error('Failed to approve review');
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-reviews'] });
    },
  });

  // Mutation reject review
  const rejectMutation = useMutation({
    mutationFn: async (reviewId: number) => {
      const token = localStorage.getItem('accessToken');
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';
      const response = await fetch(`${baseUrl}/api/reviews/admin/${reviewId}/reject`, {
        method: 'PATCH',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        credentials: 'include',
      });

      if (!response.ok) throw new Error('Failed to reject review');
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-reviews'] });
    },
  });

  // Mutation delete review
  const deleteMutation = useMutation({
    mutationFn: async (reviewId: number) => {
      const token = localStorage.getItem('accessToken');
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';
      const response = await fetch(`${baseUrl}/api/reviews/admin/${reviewId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        credentials: 'include',
      });

      if (!response.ok) throw new Error('Failed to delete review');
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-reviews'] });
    },
  });

  const handleApprove = (reviewId: number) => {
    if (confirm('Duyệt đánh giá này?')) {
      approveMutation.mutate(reviewId);
    }
  };

  const handleReject = (reviewId: number) => {
    if (confirm('Từ chối đánh giá này?')) {
      rejectMutation.mutate(reviewId);
    }
  };

  const handleDelete = (reviewId: number) => {
    if (confirm('Xóa đánh giá này?')) {
      deleteMutation.mutate(reviewId);
    }
  };

  // Handler for AI sentiment analysis
  const handleAnalyzeSentiment = async (productId: number) => {
    setLoadingSentiment(productId);
    try {
      const token = localStorage.getItem('accessToken');
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';
      const response = await fetch(`${baseUrl}/api/ai/sentiment/${productId}`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        credentials: 'include',
      });

      if (!response.ok) throw new Error('Failed to analyze sentiment');

      const data = await response.json();
      setSentimentData(prev => ({ ...prev, [productId]: data }));
      setShowSentiment(productId);
    } catch (error) {
      console.error('Error analyzing sentiment:', error);
      alert('Không thể phân tích sentiment. Vui lòng thử lại.');
    } finally {
      setLoadingSentiment(null);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <div className="p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Quản Lý Đánh Giá</h1>
      </div>

      {/* Reviews Table */}
      <div className="bg-white rounded-lg shadow-md overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                ID
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Sản Phẩm
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Người Dùng
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Đánh Giá
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Nội Dung
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Trạng Thái
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Hành Động
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {data?.content.map((review) => (
              <tr key={review.id} className="hover:bg-gray-50">
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                  #{review.id}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <div className="flex items-center gap-2">
                    <Link
                      href={`/admin/products/${review.productId}`}
                      className="text-blue-600 hover:text-blue-800 hover:underline"
                    >
                      {review.productName}
                    </Link>
                    <button
                      onClick={() => handleAnalyzeSentiment(review.productId)}
                      disabled={loadingSentiment === review.productId}
                      className="p-1 hover:bg-purple-100 rounded-lg transition-colors"
                      title="Phân tích AI Sentiment"
                    >
                      {loadingSentiment === review.productId ? (
                        <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-purple-600"></div>
                      ) : (
                        <svg className="w-4 h-4 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                        </svg>
                      )}
                    </button>
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                  {review.username}
                  {review.verified && (
                    <span className="ml-2 text-xs text-green-600">✓ Đã mua</span>
                  )}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <div className="flex items-center gap-1">
                    {[...Array(5)].map((_, i) => (
                      <svg
                        key={i}
                        className={`w-4 h-4 ${
                          i < review.rating ? 'text-yellow-400' : 'text-gray-300'
                        }`}
                        fill="currentColor"
                        viewBox="0 0 20 20"
                      >
                        <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                      </svg>
                    ))}
                  </div>
                </td>
                <td className="px-6 py-4 text-sm text-gray-900">
                  <div className="max-w-xs">
                    <div className="font-semibold">{review.title}</div>
                    <div className="text-gray-600 truncate">{review.comment}</div>
                    {review.images.length > 0 && (
                      <div className="text-xs text-gray-500 mt-1">
                        📷 {review.images.length} ảnh
                      </div>
                    )}
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  {review.approved ? (
                    <span className="px-2 py-1 text-xs font-semibold rounded-full bg-green-100 text-green-800">
                      Đã duyệt
                    </span>
                  ) : (
                    <span className="px-2 py-1 text-xs font-semibold rounded-full bg-yellow-100 text-yellow-800">
                      Chờ duyệt
                    </span>
                  )}
                  {review.reportCount > 0 && (
                    <div className="text-xs text-red-600 mt-1">
                      🚩 {review.reportCount} báo cáo
                    </div>
                  )}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <div className="flex items-center gap-2">
                    {/* View Icon */}
                    <Link
                      href={`/admin/reviews/${review.id}`}
                      className="p-2 hover:bg-blue-100 rounded-lg transition-colors"
                      title="Xem chi tiết"
                    >
                      <svg className="w-5 h-5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                      </svg>
                    </Link>

                    {/* Approve/Reject Icon */}
                    {review.approved ? (
                      <button
                        onClick={() => handleReject(review.id)}
                        className="p-2 hover:bg-orange-100 rounded-lg transition-colors"
                        title="Từ chối"
                        disabled={rejectMutation.isPending}
                      >
                        <svg className="w-5 h-5 text-orange-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
                        </svg>
                      </button>
                    ) : (
                      <button
                        onClick={() => handleApprove(review.id)}
                        className="p-2 hover:bg-green-100 rounded-lg transition-colors"
                        title="Duyệt"
                        disabled={approveMutation.isPending}
                      >
                        <svg className="w-5 h-5 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                        </svg>
                      </button>
                    )}

                    {/* Delete Icon */}
                    <button
                      onClick={() => handleDelete(review.id)}
                      className="p-2 hover:bg-red-100 rounded-lg transition-colors"
                      title="Xóa"
                      disabled={deleteMutation.isPending}
                    >
                      <svg className="w-5 h-5 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between mt-6">
        <div className="text-sm text-gray-700">
          Hiển thị {data?.content.length || 0} trên tổng {data?.totalElements || 0} đánh giá
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Trước
          </button>
          <span className="px-4 py-2 text-sm text-gray-700">
            Trang {page + 1} / {data?.totalPages || 1}
          </span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={page >= (data?.totalPages || 1) - 1}
            className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Sau
          </button>
        </div>
      </div>

      {/* AI Sentiment Analysis Modal */}
      {showSentiment && sentimentData[showSentiment] && (
        <div className="fixed inset-0  bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg shadow-xl max-w-3xl w-full max-h-[90vh] overflow-y-auto">
            {/* Modal Header */}
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-purple-100 rounded-lg flex items-center justify-center">
                  <svg className="w-6 h-6 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                  </svg>
                </div>
                <div>
                  <h2 className="text-xl font-bold text-gray-900">Phân Tích AI Sentiment</h2>
                  <p className="text-sm text-gray-500">Phân tích cảm xúc đánh giá sản phẩm</p>
                </div>
              </div>
              <button
                onClick={() => setShowSentiment(null)}
                className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
              >
                <svg className="w-5 h-5 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Modal Content */}
            <div className="p-6 space-y-6">
              {/* Product Info & Rating */}
              <div className="grid grid-cols-2 gap-4">
                <div className="bg-blue-50 rounded-lg p-4">
                  <div className="flex items-center gap-2 mb-2">
                    <svg className="w-5 h-5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                    </svg>
                    <span className="text-sm font-medium text-blue-900">Rating Trung Bình</span>
                  </div>
                  <div className="text-3xl font-bold text-blue-600">
                    {sentimentData[showSentiment].data.averageRating.toFixed(1)}/5
                  </div>
                  <div className="text-xs text-blue-700 mt-1">
                    {sentimentData[showSentiment].data.totalReviews} đánh giá
                  </div>
                </div>

                <div className={`rounded-lg p-4 ${
                  sentimentData[showSentiment].data.sentiment === 'Positive' ? 'bg-green-50' :
                  sentimentData[showSentiment].data.sentiment === 'Negative' ? 'bg-red-50' : 'bg-gray-50'
                }`}>
                  <div className="flex items-center gap-2 mb-2">
                    {sentimentData[showSentiment].data.sentiment === 'Positive' ? (
                      <svg className="w-5 h-5 text-green-600" fill="currentColor" viewBox="0 0 20 20">
                        <path d="M10 18a8 8 0 100-16 8 8 0 000 16zM7 9a1 1 0 100-2 1 1 0 000 2zm7-1a1 1 0 11-2 0 1 1 0 012 0zm-.464 5.535a1 1 0 10-1.415-1.414 3 3 0 01-4.242 0 1 1 0 00-1.415 1.414 5 5 0 007.072 0z" />
                      </svg>
                    ) : sentimentData[showSentiment].data.sentiment === 'Negative' ? (
                      <svg className="w-5 h-5 text-red-600" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM7 9a1 1 0 100-2 1 1 0 000 2zm7-1a1 1 0 11-2 0 1 1 0 012 0zm-.464 5.535a1 1 0 10-1.415-1.414 3 3 0 01-4.242 0 1 1 0 00-1.415 1.414 5 5 0 007.072 0z" clipRule="evenodd" />
                      </svg>
                    ) : (
                      <svg className="w-5 h-5 text-gray-600" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM7 9a1 1 0 100-2 1 1 0 000 2zm7-1a1 1 0 11-2 0 1 1 0 012 0zm-7.536 5.879a1 1 0 001.415 0 3 3 0 014.242 0 1 1 0 001.415-1.415 5 5 0 00-7.072 0 1 1 0 000 1.415z" clipRule="evenodd" />
                      </svg>
                    )}
                    <span className={`text-sm font-medium ${
                      sentimentData[showSentiment].data.sentiment === 'Positive' ? 'text-green-900' :
                      sentimentData[showSentiment].data.sentiment === 'Negative' ? 'text-red-900' : 'text-gray-900'
                    }`}>Sentiment</span>
                  </div>
                  <div className={`text-2xl font-bold ${
                    sentimentData[showSentiment].data.sentiment === 'Positive' ? 'text-green-600' :
                    sentimentData[showSentiment].data.sentiment === 'Negative' ? 'text-red-600' : 'text-gray-600'
                  }`}>
                    {sentimentData[showSentiment].data.sentiment}
                  </div>
                </div>
              </div>

              {/* Summary */}
              {sentimentData[showSentiment].data.summary && (
                <div className="bg-blue-50 rounded-lg p-4">
                  <div className="flex items-center gap-2 mb-2">
                    <svg className="w-5 h-5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <span className="text-sm font-semibold text-blue-900">Tóm Tắt</span>
                  </div>
                  <p className="text-blue-900">
                    {sentimentData[showSentiment].data.summary}
                  </p>
                </div>
              )}

              {/* Positive & Negative Points */}
              {(sentimentData[showSentiment].data.positivePoints || sentimentData[showSentiment].data.negativePoints) && (
                <div className="grid grid-cols-2 gap-4">
                  {sentimentData[showSentiment].data.positivePoints && sentimentData[showSentiment].data.positivePoints.length > 0 && (
                    <div className="bg-green-50 rounded-lg p-4">
                      <div className="flex items-center gap-2 mb-3">
                        <svg className="w-5 h-5 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                        </svg>
                        <span className="text-sm font-semibold text-green-900">Điểm Tích Cực</span>
                      </div>
                      <ul className="space-y-1">
                        {sentimentData[showSentiment].data.positivePoints.map((point, idx) => (
                          <li key={idx} className="text-sm text-green-800 flex items-start gap-2">
                            <span className="text-green-600 mt-0.5">•</span>
                            <span>{point}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {sentimentData[showSentiment].data.negativePoints && sentimentData[showSentiment].data.negativePoints.length > 0 && (
                    <div className="bg-red-50 rounded-lg p-4">
                      <div className="flex items-center gap-2 mb-3">
                        <svg className="w-5 h-5 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                        <span className="text-sm font-semibold text-red-900">Điểm Tiêu Cực</span>
                      </div>
                      <ul className="space-y-1">
                        {sentimentData[showSentiment].data.negativePoints.map((point, idx) => (
                          <li key={idx} className="text-sm text-red-800 flex items-start gap-2">
                            <span className="text-red-600 mt-0.5">•</span>
                            <span>{point}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}

              {/* AI Explanation */}
              <div className="bg-gradient-to-r from-purple-50 to-pink-50 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-3">
                  <svg className="w-5 h-5 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                  </svg>
                  <span className="text-sm font-semibold text-purple-900">Phân Tích AI</span>
                </div>
                <p className="text-gray-700 whitespace-pre-wrap leading-relaxed">
                  {sentimentData[showSentiment].aiExplanation}
                </p>
              </div>

              {/* Metadata */}
              <div className="border-t border-gray-200 pt-4">
                <div className="text-xs text-gray-500">
                  <div className="mb-2">
                    <span className="font-semibold">Tổng số đánh giá:</span> {sentimentData[showSentiment].data.totalReviews}
                  </div>
                </div>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200">
              <button
                onClick={() => setShowSentiment(null)}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors font-medium"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
