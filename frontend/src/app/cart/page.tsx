/**
 * Shopping Cart Page
 * Strategy: CSR (Client-Side Rendering)
 * Requires authentication
 */

'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { getCart, updateCartItem, removeFromCart, clearCart } from '@/lib/api/cart';
import { getAvailableCoupons, applyCoupon } from '@/lib/api/coupons';
import Link from 'next/link';
import { useState, useEffect } from 'react';

export default function CartPage() {
  const router = useRouter();
  const { isAuthenticated, loading: authLoading } = useAuth();
  const queryClient = useQueryClient();
  const [couponCode, setCouponCode] = useState('');

  // Redirect if not authenticated
  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.push('/login?redirect=/cart');
    }
  }, [isAuthenticated, authLoading, router]);

  // Fetch cart data
  const { data: cart, isLoading: cartLoading } = useQuery({
    queryKey: ['cart'],
    queryFn: getCart,
    enabled: isAuthenticated,
  });

  // Fetch available coupons
  const { data: coupons } = useQuery({
    queryKey: ['available-coupons'],
    queryFn: getAvailableCoupons,
    enabled: isAuthenticated,
  });

  // Update cart item mutation
  const updateItemMutation = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: number; quantity: number }) =>
      updateCartItem(itemId, { quantity }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] });
    },
  });

  // Remove item mutation
  const removeItemMutation = useMutation({
    mutationFn: removeFromCart,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] });
    },
  });

  // Clear cart mutation
  const clearCartMutation = useMutation({
    mutationFn: clearCart,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] });
    },
  });

  // Apply coupon mutation
  const applyCouponMutation = useMutation({
    mutationFn: () => applyCoupon(couponCode),
    onSuccess: (response) => {
      if (response.valid) {
        alert('Coupon applied successfully!');
        queryClient.invalidateQueries({ queryKey: ['cart'] });
        setCouponCode('');
      } else {
        alert(response.message);
      }
    },
    onError: (error: any) => {
      alert('Failed to apply coupon: ' + error.message);
    },
  });

  if (authLoading || cartLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600 dark:text-gray-400">Loading cart...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  if (!cart || cart.items.length === 0) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
          <div className="text-center">
            <h1 className="text-4xl font-bold text-gray-900 dark:text-white mb-4">
              Your Cart is Empty
            </h1>
            <p className="text-gray-600 dark:text-gray-400 mb-8">
              Add some products to get started!
            </p>
            <Link
              href="/shop"
              className="inline-block px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              Continue Shopping
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-8">
          Shopping Cart
        </h1>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Cart Items */}
          <div className="lg:col-span-2 space-y-4">
            {cart.items.map((item) => (
              <div
                key={item.id}
                className="bg-white dark:bg-gray-800 rounded-lg shadow p-6 flex gap-6"
              >
                <img
                  src={item.productImage}
                  alt={item.productName}
                  className="w-24 h-24 object-cover rounded-lg"
                />

                <div className="flex-1">
                  <Link
                    href={`/product/${item.productSlug}`}
                    className="text-lg font-semibold text-gray-900 dark:text-white hover:text-blue-600"
                  >
                    {item.productName}
                  </Link>

                  <div className="mt-2">
                    {item.discountPrice ? (
                      <div className="flex items-center gap-2">
                        <span className="text-lg font-bold text-red-600">
                          ${item.discountPrice.toFixed(2)}
                        </span>
                        <span className="text-sm text-gray-500 line-through">
                          ${item.price.toFixed(2)}
                        </span>
                      </div>
                    ) : (
                      <span className="text-lg font-bold text-gray-900 dark:text-white">
                        ${item.price.toFixed(2)}
                      </span>
                    )}
                  </div>

                  {!item.available && (
                    <p className="text-red-500 text-sm mt-2">Out of stock</p>
                  )}

                  <div className="flex items-center gap-4 mt-4">
                    {/* Quantity Selector */}
                    <div className="flex items-center border rounded-lg">
                      <button
                        onClick={() =>
                          updateItemMutation.mutate({
                            itemId: item.id,
                            quantity: Math.max(1, item.quantity - 1),
                          })
                        }
                        disabled={item.quantity <= 1 || updateItemMutation.isPending}
                        className="px-3 py-1 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-50"
                      >
                        -
                      </button>
                      <span className="px-4 py-1 border-x">{item.quantity}</span>
                      <button
                        onClick={() =>
                          updateItemMutation.mutate({
                            itemId: item.id,
                            quantity: Math.min(item.stockQuantity, item.quantity + 1),
                          })
                        }
                        disabled={
                          item.quantity >= item.stockQuantity ||
                          updateItemMutation.isPending
                        }
                        className="px-3 py-1 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-50"
                      >
                        +
                      </button>
                    </div>

                    {/* Remove Button */}
                    <button
                      onClick={() => removeItemMutation.mutate(item.id)}
                      disabled={removeItemMutation.isPending}
                      className="text-red-600 hover:text-red-700 text-sm"
                    >
                      Remove
                    </button>
                  </div>

                  <div className="mt-2 text-sm text-gray-600 dark:text-gray-400">
                    Subtotal: ${item.subtotal.toFixed(2)}
                  </div>
                </div>
              </div>
            ))}

            <button
              onClick={() => {
                if (confirm('Are you sure you want to clear your cart?')) {
                  clearCartMutation.mutate();
                }
              }}
              disabled={clearCartMutation.isPending}
              className="text-red-600 hover:text-red-700 text-sm"
            >
              Clear Cart
            </button>
          </div>

          {/* Order Summary */}
          <div>
            <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6 sticky top-4">
              <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
                Order Summary
              </h2>

              <div className="space-y-3 mb-4">
                <div className="flex justify-between">
                  <span className="text-gray-600 dark:text-gray-400">Subtotal</span>
                  <span className="font-semibold">${cart.subtotal.toFixed(2)}</span>
                </div>
                {cart.discount > 0 && (
                  <div className="flex justify-between text-green-600">
                    <span>Discount</span>
                    <span>-${cart.discount.toFixed(2)}</span>
                  </div>
                )}
                <div className="border-t pt-3 flex justify-between text-lg font-bold">
                  <span>Total</span>
                  <span>${cart.total.toFixed(2)}</span>
                </div>
              </div>

              {/* Coupon Code */}
              <div className="mb-4">
                <label className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2 block">
                  Have a coupon code?
                </label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={couponCode}
                    onChange={(e) => setCouponCode(e.target.value)}
                    placeholder="Enter code"
                    className="flex-1 px-3 py-2 border rounded-md text-sm dark:bg-gray-700 dark:border-gray-600"
                  />
                  <button
                    onClick={() => applyCouponMutation.mutate()}
                    disabled={!couponCode || applyCouponMutation.isPending}
                    className="px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-md hover:bg-gray-300 dark:hover:bg-gray-600 disabled:opacity-50"
                  >
                    Apply
                  </button>
                </div>

                {/* Available Coupons */}
                {coupons && coupons.length > 0 && (
                  <div className="mt-3">
                    <p className="text-xs text-gray-500 mb-2">Available coupons:</p>
                    <div className="space-y-1">
                      {coupons.slice(0, 3).map((coupon) => (
                        <button
                          key={coupon.id}
                          onClick={() => setCouponCode(coupon.code)}
                          className="text-xs text-blue-600 hover:underline block"
                        >
                          {coupon.code} - {coupon.description}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              <button
                onClick={() => router.push('/checkout')}
                className="w-full px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-semibold"
              >
                Proceed to Checkout
              </button>

              <Link
                href="/shop"
                className="block text-center mt-4 text-blue-600 hover:underline"
              >
                Continue Shopping
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
