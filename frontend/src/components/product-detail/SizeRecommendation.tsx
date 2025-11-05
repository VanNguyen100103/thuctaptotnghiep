/**
 * AI Size Recommendation Component
 * Shows AI-powered size recommendation based on user measurements
 * REQUIRES AUTHENTICATION
 */

'use client';

import { useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';

interface SizeRecommendationProps {
  productId: number;
}

export default function SizeRecommendation({ productId }: SizeRecommendationProps) {
  const { isAuthenticated } = useAuth();
  const [height, setHeight] = useState<string>('');
  const [weight, setWeight] = useState<string>('');
  const [recommendation, setRecommendation] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  // Don't show if user is not authenticated
  if (!isAuthenticated) {
    return null;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Validate input
    const heightNum = parseInt(height);
    const weightNum = parseInt(weight);

    if (!height || !weight) {
      setError('Vui lòng nhập đầy đủ chiều cao và cân nặng');
      return;
    }

    if (heightNum < 100 || heightNum > 250) {
      setError('Chiều cao phải từ 100-250 cm');
      return;
    }

    if (weightNum < 30 || weightNum > 200) {
      setError('Cân nặng phải từ 30-200 kg');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      setRecommendation('');

      // Get auth token
      const token = localStorage.getItem('accessToken');
      if (!token) {
        setError('Vui lòng đăng nhập để sử dụng tính năng này');
        return;
      }

      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      };

      // Use API base URL from environment or relative path
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || '';
      const response = await fetch(
        `${baseUrl}/api/ai/size-recommendation?productId=${productId}&height=${heightNum}&weight=${weightNum}`,
        {
          headers,
          credentials: 'include',
        }
      );

      if (!response.ok) {
        throw new Error('Không thể lấy gợi ý size');
      }

      const data = await response.json();
      console.log('[SizeRecommendation] Response data:', data);

      if (data.recommendation) {
        // Clean up markdown formatting
        const cleanRecommendation = data.recommendation
          .replace(/#{1,6}\s*/g, '') // Remove ### heading markers
          .replace(/\*\*/g, '') // Remove ** bold markers
          .replace(/\*/g, '') // Remove * italic markers
          .replace(/^-\s*/gm, '• ') // Replace - with bullet points
          .trim();

        setRecommendation(cleanRecommendation);
      }
    } catch (err) {
      console.error('Error fetching size recommendation:', err);
      setError('Không thể tải gợi ý size. Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setHeight('');
    setWeight('');
    setRecommendation('');
    setError(null);
    setShowForm(false);
  };

  return (
    <div className="bg-gradient-to-r from-indigo-50 to-blue-50 rounded-xl p-6 mb-8 border-2 border-indigo-200 shadow-sm">
      <div className="flex items-start gap-4">
        <div className="flex-shrink-0 w-12 h-12 bg-gradient-to-br from-indigo-500 to-blue-500 rounded-full flex items-center justify-center">
          <span className="text-2xl">📏</span>
        </div>
        <div className="flex-1">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <span>🤖</span>
              Hướng dẫn chọn size từ AI
            </h3>
            {!showForm && !recommendation && (
              <button
                onClick={() => setShowForm(true)}
                className="text-sm bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg font-semibold transition-colors"
              >
                Nhận gợi ý size
              </button>
            )}
          </div>

          {/* Form nhập chiều cao và cân nặng */}
          {showForm && !recommendation && (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="height" className="block text-sm font-medium text-gray-700 mb-2">
                    Chiều cao (cm) <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="number"
                    id="height"
                    value={height}
                    onChange={(e) => setHeight(e.target.value)}
                    placeholder="Ví dụ: 170"
                    min="100"
                    max="250"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                    required
                  />
                  <p className="text-xs text-gray-500 mt-1">Từ 100-250 cm</p>
                </div>

                <div>
                  <label htmlFor="weight" className="block text-sm font-medium text-gray-700 mb-2">
                    Cân nặng (kg) <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="number"
                    id="weight"
                    value={weight}
                    onChange={(e) => setWeight(e.target.value)}
                    placeholder="Ví dụ: 65"
                    min="30"
                    max="200"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                    required
                  />
                  <p className="text-xs text-gray-500 mt-1">Từ 30-200 kg</p>
                </div>
              </div>

              {error && (
                <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
                  {error}
                </div>
              )}

              <div className="flex gap-3">
                <button
                  type="submit"
                  disabled={loading}
                  className="flex-1 bg-indigo-600 hover:bg-indigo-700 disabled:bg-gray-400 text-white px-6 py-3 rounded-lg font-semibold transition-colors flex items-center justify-center gap-2"
                >
                  {loading ? (
                    <>
                      <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                      Đang xử lý...
                    </>
                  ) : (
                    <>
                      <span>🎯</span>
                      Nhận gợi ý size
                    </>
                  )}
                </button>
                <button
                  type="button"
                  onClick={handleReset}
                  className="px-6 py-3 border border-gray-300 rounded-lg font-semibold hover:bg-gray-50 transition-colors"
                >
                  Hủy
                </button>
              </div>
            </form>
          )}

          {/* AI Recommendation Result */}
          {recommendation && (
            <div className="space-y-4">
              <div className="bg-white rounded-xl p-4 border-2 border-indigo-200 shadow-sm">
                <div className="flex items-start gap-3 mb-3">
                  <span className="text-2xl">✅</span>
                  <div className="flex-1">
                    <h4 className="font-semibold text-gray-900 mb-2">Gợi ý size từ AI:</h4>
                    <div className="text-sm text-gray-700 leading-relaxed whitespace-pre-line">
                      {recommendation}
                    </div>
                  </div>
                </div>

                <div className="mt-4 pt-4 border-t border-gray-200 flex items-center gap-2 text-xs text-gray-500">
                  <span>📊</span>
                  <span>Dựa trên chiều cao: {height}cm, cân nặng: {weight}kg</span>
                </div>
              </div>

              <div className="flex gap-3">
                <button
                  onClick={handleReset}
                  className="flex-1 bg-indigo-600 hover:bg-indigo-700 text-white px-6 py-2 rounded-lg font-semibold transition-colors"
                >
                  Thử lại với số đo khác
                </button>
              </div>
            </div>
          )}

          {/* Info text when form is not shown */}
          {!showForm && !recommendation && (
            <p className="text-sm text-gray-600">
              Nhập chiều cao và cân nặng của bạn để AI gợi ý size phù hợp nhất cho sản phẩm này.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
