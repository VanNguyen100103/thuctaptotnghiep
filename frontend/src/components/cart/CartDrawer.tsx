/**
 * Cart Drawer Component
 * Slide-out shopping cart sidebar
 */

'use client';

import { Fragment } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getCart, clearCart } from '@/lib/api/cart';
import CartItem from './CartItem';

interface CartDrawerProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function CartDrawer({ isOpen, onClose }: CartDrawerProps) {
  const router = useRouter();
  const queryClient = useQueryClient();

  // Fetch cart data
  const { data: cart, isLoading, error } = useQuery({
    queryKey: ['cart'],
    queryFn: getCart,
    enabled: isOpen, // Only fetch when drawer is open
    retry: false,
  });

  // Clear cart mutation
  const clearCartMutation = useMutation({
    mutationFn: clearCart,
    onSuccess: (response) => {
      // Update cart cache with new data (no need to invalidate)
      queryClient.setQueryData(['cart'], response.cart);
      // Also update cart count
      queryClient.setQueryData(['cartCount'], { count: 0 });
    },
    onError: (error: any) => {
      alert(`Lỗi: ${error.message || 'Không thể xóa giỏ hàng'}`);
    },
  });

  const handleClearCart = () => {
    if (confirm('Bạn có chắc muốn xóa toàn bộ giỏ hàng?')) {
      clearCartMutation.mutate();
    }
  };

  const handleCheckout = () => {
    onClose();
    router.push('/checkout');
  };

  if (!isOpen) return null;

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0  bg-opacity-50 z-40 transition-opacity"
        onClick={onClose}
      />

      {/* Drawer */}
      <div className="fixed right-0 top-0 h-full w-full max-w-md bg-white shadow-xl z-50 flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <h2 className="text-xl font-bold text-gray-900">
            Giỏ hàng {cart && cart.totalItems > 0 && `(${cart.totalItems})`}
          </h2>
          <button
            onClick={onClose}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-4">
          {isLoading && (
            <div className="flex items-center justify-center h-32">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
            </div>
          )}

          {error && (
            <div className="text-center py-8">
              <p className="text-red-600 mb-4">
                {error instanceof Error ? error.message : 'Không thể tải giỏ hàng'}
              </p>
              <button
                onClick={() => queryClient.invalidateQueries({ queryKey: ['cart'] })}
                className="text-blue-600 hover:underline"
              >
                Thử lại
              </button>
            </div>
          )}

          {!isLoading && !error && cart && cart.items.length === 0 && (
            <div className="text-center py-12">
              <svg className="w-24 h-24 mx-auto text-gray-300 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
              </svg>
              <p className="text-gray-500 mb-4">
                Giỏ hàng của bạn đang trống
              </p>
              <button
                onClick={onClose}
                className="text-blue-600 hover:underline"
              >
                Tiếp tục mua sắm
              </button>
            </div>
          )}

          {!isLoading && !error && cart && cart.items.length > 0 && (
            <>
              {/* Cart Items */}
              <div className="space-y-0">
                {cart.items.map((item) => (
                  <CartItem key={item.id} item={item} />
                ))}
              </div>

              {/* Clear Cart Button */}
              {cart.items.length > 0 && (
                <button
                  onClick={handleClearCart}
                  disabled={clearCartMutation.isPending}
                  className="w-full mt-4 text-sm text-red-600 hover:text-red-700 disabled:opacity-50"
                >
                  {clearCartMutation.isPending ? 'Đang xóa...' : 'Xóa toàn bộ giỏ hàng'}
                </button>
              )}
            </>
          )}
        </div>

        {/* Footer - Checkout */}
        {!isLoading && !error && cart && cart.items.length > 0 && (
          <div className="border-t border-gray-200 p-4 space-y-4">
            {/* Total */}
            <div className="flex items-center justify-between text-lg font-bold">
              <span className="text-gray-900">Tổng cộng:</span>
              <span className="text-blue-600">
                {cart.totalPrice.toLocaleString('vi-VN')} ₫
              </span>
            </div>

            {/* Checkout Button */}
            <button
              onClick={handleCheckout}
              className="w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3 px-4 rounded-lg transition-colors"
            >
              Thanh toán
            </button>

            {/* Continue Shopping */}
            <button
              onClick={onClose}
              className="w-full border border-gray-300 hover:bg-gray-50 text-gray-700 font-medium py-3 px-4 rounded-lg transition-colors"
            >
              Tiếp tục mua sắm
            </button>
          </div>
        )}
      </div>
    </>
  );
}
