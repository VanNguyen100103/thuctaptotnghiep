/**
 * Payment Success Page
 * Handles PayPal return after successful approval
 * Executes payment and shows confirmation
 */

'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { executePayment } from '@/lib/api/payments';

export default function PaymentSuccessPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();

  const [isProcessing, setIsProcessing] = useState(true);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');
  const [orderNumber, setOrderNumber] = useState('');
  const [transactionId, setTransactionId] = useState('');

  useEffect(() => {
    const paymentId = searchParams.get('paymentId');
    const payerId = searchParams.get('PayerID');

    console.log('[Payment Success] Query params:', { paymentId, payerId });

    if (!paymentId || !payerId) {
      setError('Missing payment information. Please contact support.');
      setIsProcessing(false);
      return;
    }

    // Execute payment
    const executePaymentAsync = async () => {
      try {
        console.log('[Payment Success] Executing payment...');
        const response = await executePayment({ paymentId, payerId });

        console.log('[Payment Success] Payment executed successfully:', response);
        setSuccess(true);
        setOrderNumber(response.orderNumber);
        setTransactionId(response.transactionId);

        // Clear cart cache and refetch immediately (cart was cleared on backend)
        await queryClient.refetchQueries({ queryKey: ['cart'] });
        await queryClient.refetchQueries({ queryKey: ['cartCount'] });

        // Update orders cache
        queryClient.invalidateQueries({ queryKey: ['orders'] });
      } catch (err: any) {
        console.error('[Payment Success] Payment execution failed:', err);
        setError(err.message || 'Failed to complete payment. Please contact support.');
      } finally {
        setIsProcessing(false);
      }
    };

    executePaymentAsync();
  }, [searchParams, queryClient]);

  if (isProcessing) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-16 w-16 border-b-4 border-blue-600 mx-auto mb-4"></div>
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Đang Xử Lý Thanh Toán...</h2>
          <p className="text-gray-600">Vui lòng đợi trong khi chúng tôi xác nhận thanh toán của bạn</p>
          <p className="text-sm text-gray-500 mt-2">Không đóng cửa sổ này</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="max-w-md w-full bg-white rounded-lg shadow-lg p-8 text-center">
          {/* Error Icon */}
          <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg
              className="w-8 h-8 text-red-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </div>

          <h1 className="text-2xl font-bold text-gray-900 mb-2">Lỗi Thanh Toán</h1>
          <p className="text-gray-600 mb-6">{error}</p>

          <div className="space-y-3">
            <Link
              href="/orders"
              className="block w-full px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-semibold"
            >
              Xem Đơn Hàng Của Tôi
            </Link>
            <Link
              href="/products"
              className="block w-full px-6 py-3 border border-gray-300 rounded-lg hover:bg-gray-50"
            >
              Tiếp Tục Mua Sắm
            </Link>
          </div>

          <p className="text-sm text-gray-500 mt-4">
            Cần trợ giúp? Liên hệ với đội ngũ hỗ trợ của chúng tôi
          </p>
        </div>
      </div>
    );
  }

  if (success) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="max-w-md w-full bg-white rounded-lg shadow-lg p-8 text-center">
          {/* Success Icon */}
          <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg
              className="w-8 h-8 text-green-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M5 13l4 4L19 7"
              />
            </svg>
          </div>

          <h1 className="text-2xl font-bold text-gray-900 mb-2">Thanh Toán Thành Công!</h1>
          <p className="text-gray-600 mb-6">
            Cảm ơn bạn đã đặt hàng. Chúng tôi đã gửi email xác nhận với thông tin đơn hàng của bạn.
          </p>

          {/* Order Details */}
          <div className="bg-gray-50 rounded-lg p-4 mb-6 text-left">
            <div className="flex justify-between py-2 border-b">
              <span className="text-gray-600">Mã Đơn Hàng:</span>
              <span className="font-semibold">{orderNumber}</span>
            </div>
            <div className="flex justify-between py-2">
              <span className="text-gray-600">Mã Giao Dịch:</span>
              <span className="font-mono text-sm">{transactionId}</span>
            </div>
          </div>

          <div className="space-y-3">
            <Link
              href={`/orders`}
              className="block w-full px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-semibold"
            >
              Xem Chi Tiết Đơn Hàng
            </Link>
            <Link
              href="/products"
              className="block w-full px-6 py-3 border border-gray-300 rounded-lg hover:bg-gray-50"
            >
              Tiếp Tục Mua Sắm
            </Link>
          </div>

          <p className="text-sm text-gray-500 mt-4">
            Bạn sẽ nhận được cập nhật đơn hàng qua email
          </p>
        </div>
      </div>
    );
  }

  return null;
}
