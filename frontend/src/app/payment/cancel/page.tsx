/**
 * Payment Cancel Page
 * Shown when user cancels PayPal payment
 */

'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';

export default function PaymentCancelPage() {
  const router = useRouter();

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-lg shadow-lg p-8 text-center">
        {/* Warning Icon */}
        <div className="w-16 h-16 bg-yellow-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <svg
            className="w-8 h-8 text-yellow-600"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
        </div>

        <h1 className="text-2xl font-bold text-gray-900 mb-2">Payment Cancelled</h1>
        <p className="text-gray-600 mb-6">
          You have cancelled the payment process. Your order has been created but not paid yet.
        </p>

        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6 text-left">
          <p className="text-sm text-blue-800">
            <strong>Note:</strong> Your order is still pending. You can complete payment later from your orders page,
            or the order will be automatically cancelled after 24 hours.
          </p>
        </div>

        <div className="space-y-3">
          <button
            onClick={() => router.push('/checkout')}
            className="w-full px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-semibold"
          >
            Try Again
          </button>
          <Link
            href="/orders"
            className="block w-full px-6 py-3 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            View My Orders
          </Link>
          <Link
            href="/cart"
            className="block w-full px-6 py-3 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            Return to Cart
          </Link>
        </div>

        <p className="text-sm text-gray-500 mt-4">
          Need help? Contact our support team
        </p>
      </div>
    </div>
  );
}
