/**
 * Styling Tips Component
 * Shows AI-powered styling tips for a product
 * REQUIRES AUTHENTICATION
 * Button-triggered pattern (like SizeRecommendation)
 */

'use client';

import { useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';

interface StylingTipsProps {
  productId: number;
}

export default function StylingTips({ productId }: StylingTipsProps) {
  const { isAuthenticated } = useAuth();
  const [tips, setTips] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasRequested, setHasRequested] = useState(false);

  const fetchStylingTips = async () => {
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
      const response = await fetch(`${baseUrl}/api/ai/styling-tips/${productId}`, {
        headers,
        credentials: 'include',
      });

      if (!response.ok) {
        throw new Error('Failed to fetch styling tips');
      }

      const data = await response.json();
      console.log('[StylingTips] Response data:', data);

      if (data.tips) {
        setTips(data.tips);
        setHasRequested(true);
      }
    } catch (err) {
      console.error('Error fetching styling tips:', err);
      setError('Không thể tải gợi ý phối đồ');
    } finally {
      setLoading(false);
    }
  };

  // Don't show if user is not authenticated
  if (!isAuthenticated) {
    return null;
  }

  // Show button if tips haven't been requested yet
  if (!hasRequested && !loading) {
    return (
      <div className="bg-gradient-to-r from-green-50 to-teal-50 rounded-xl p-6 mb-8 border-2 border-green-200 shadow-sm">
        <div className="flex items-start gap-4">
          <div className="flex-shrink-0 w-12 h-12 bg-gradient-to-br from-green-500 to-teal-500 rounded-full flex items-center justify-center">
            <span className="text-2xl">👔</span>
          </div>
          <div className="flex-1">
            <h3 className="text-lg font-bold text-gray-900 mb-3 flex items-center gap-2">
              <span>✨</span>
              Gợi ý phối đồ từ AI
            </h3>
            <p className="text-sm text-gray-600 mb-4">
              Nhận gợi ý cách phối đồ phù hợp với sản phẩm này từ AI
            </p>
            <button
              onClick={fetchStylingTips}
              className="px-6 py-2 bg-gradient-to-r from-green-500 to-teal-500 text-white rounded-lg hover:from-green-600 hover:to-teal-600 transition-all shadow-sm hover:shadow-md"
            >
              Nhận gợi ý phối đồ
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Show loading state
  if (loading) {
    return (
      <div className="bg-gradient-to-r from-green-50 to-teal-50 rounded-xl p-6 mb-8 border-2 border-green-200 shadow-sm">
        <div className="flex items-center gap-3">
          <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-green-600"></div>
          <span className="text-sm text-gray-600">Đang tải gợi ý phối đồ...</span>
        </div>
      </div>
    );
  }

  // Show error state
  if (error) {
    return (
      <div className="bg-gradient-to-r from-green-50 to-teal-50 rounded-xl p-6 mb-8 border-2 border-green-200 shadow-sm">
        <div className="flex items-start gap-4">
          <div className="flex-shrink-0 w-12 h-12 bg-gradient-to-br from-red-500 to-red-600 rounded-full flex items-center justify-center">
            <span className="text-2xl">⚠️</span>
          </div>
          <div className="flex-1">
            <h3 className="text-lg font-bold text-gray-900 mb-2">Có lỗi xảy ra</h3>
            <p className="text-sm text-red-600 mb-4">{error}</p>
            <button
              onClick={fetchStylingTips}
              className="px-4 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600 transition-colors"
            >
              Thử lại
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Don't show if no tips
  if (!tips) {
    return null;
  }

  // Clean up markdown formatting: remove #, *, and format nicely
  const cleanTips = tips
    .replace(/#{1,6}\s*/g, '') // Remove ### heading markers
    .replace(/\*\*/g, '') // Remove ** bold markers
    .replace(/\*/g, '') // Remove * italic markers
    .replace(/^-\s*/gm, '• ') // Replace - with bullet points
    .trim();

  return (
    <div className="bg-gradient-to-r from-green-50 to-teal-50 rounded-xl p-6 mb-8 border-2 border-green-200 shadow-sm">
      <div className="flex items-start gap-4">
        <div className="flex-shrink-0 w-12 h-12 bg-gradient-to-br from-green-500 to-teal-500 rounded-full flex items-center justify-center">
          <span className="text-2xl">👔</span>
        </div>
        <div className="flex-1">
          <h3 className="text-lg font-bold text-gray-900 mb-3 flex items-center gap-2">
            <span>✨</span>
            Gợi ý phối đồ từ AI
          </h3>
          <div className="text-sm text-gray-700 leading-relaxed whitespace-pre-line">
            {cleanTips}
          </div>
        </div>
      </div>
    </div>
  );
}
