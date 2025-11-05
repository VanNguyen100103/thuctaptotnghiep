/**
 * Cart Item Component
 * Displays individual cart item with quantity controls
 */

'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateCartItem, removeFromCart } from '@/lib/api/cart';
import type { CartItem as CartItemType } from '@/types/cart';

interface CartItemProps {
  item: CartItemType;
}

export default function CartItem({ item }: CartItemProps) {
  const queryClient = useQueryClient();
  const [isUpdating, setIsUpdating] = useState(false);

  // Update quantity mutation
  const updateMutation = useMutation({
    mutationFn: (quantity: number) => updateCartItem(item.id, { quantity }),
    onMutate: () => setIsUpdating(true),
    onSuccess: (response) => {
      // Update cart cache with new data (no need to invalidate)
      queryClient.setQueryData(['cart'], response.cart);
      // Also update cart count
      queryClient.setQueryData(['cartCount'], { count: response.cart.totalItems });
    },
    onError: (error: any) => {
      alert(`Lỗi cập nhật: ${error.message || 'Không thể cập nhật số lượng'}`);
    },
    onSettled: () => setIsUpdating(false),
  });

  // Remove item mutation
  const removeMutation = useMutation({
    mutationFn: () => removeFromCart(item.id),
    onSuccess: (response) => {
      // Update cart cache with new data (no need to invalidate)
      queryClient.setQueryData(['cart'], response.cart);
      // Also update cart count
      queryClient.setQueryData(['cartCount'], { count: response.cart.totalItems });
    },
    onError: (error: any) => {
      alert(`Lỗi xóa: ${error.message || 'Không thể xóa sản phẩm'}`);
    },
  });

  const handleQuantityChange = (newQuantity: number) => {
    if (newQuantity < 1) return;
    if (newQuantity > item.stockAvailable) {
      alert(`Chỉ còn ${item.stockAvailable} sản phẩm trong kho`);
      return;
    }
    updateMutation.mutate(newQuantity);
  };

  const handleRemove = () => {
    if (confirm('Bạn có chắc muốn xóa sản phẩm này khỏi giỏ hàng?')) {
      removeMutation.mutate();
    }
  };

  return (
    <div className="flex gap-4 py-4 border-b border-gray-200">
      {/* Product Image */}
      <Link href={`/product/${item.productSlug}`} className="flex-shrink-0">
        <img
          src={item.productImage || 'https://placehold.co/400x400?text=No+Image'}
          alt={item.productName}
          className="w-24 h-24 object-cover rounded-lg"
        />
      </Link>

      {/* Product Details */}
      <div className="flex-1 min-w-0">
        <Link
          href={`/product/${item.productSlug}`}
          className="block font-medium text-gray-900 hover:text-blue-600 mb-1"
        >
          {item.productName}
        </Link>

        {/* Size & Color */}
        <div className="flex gap-3 text-sm text-gray-600 mb-2">
          {item.size && (
            <span>
              Size: <span className="font-medium">{item.size}</span>
            </span>
          )}
          {item.color && (
            <span>
              Màu: <span className="font-medium">{item.color}</span>
            </span>
          )}
        </div>

        {/* Price */}
        <div className="text-blue-600 font-semibold mb-3">
          {item.priceAtAdd.toLocaleString('vi-VN')} ₫
        </div>

        {/* Quantity Controls */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => handleQuantityChange(item.quantity - 1)}
            disabled={isUpdating || item.quantity <= 1}
            className="w-8 h-8 rounded-md border border-gray-300 flex items-center justify-center hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            -
          </button>

          <span className="w-12 text-center font-medium">
            {item.quantity}
          </span>

          <button
            onClick={() => handleQuantityChange(item.quantity + 1)}
            disabled={isUpdating || item.quantity >= item.stockAvailable}
            className="w-8 h-8 rounded-md border border-gray-300 flex items-center justify-center hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            +
          </button>

          {/* Stock warning */}
          {item.stockAvailable < 5 && (
            <span className="text-xs text-orange-600 ml-2">
              Chỉ còn {item.stockAvailable}
            </span>
          )}
        </div>
      </div>

      {/* Subtotal & Remove */}
      <div className="flex flex-col items-end justify-between">
        <div className="text-lg font-bold text-gray-900">
          {item.subtotal.toLocaleString('vi-VN')} ₫
        </div>

        <button
          onClick={handleRemove}
          disabled={removeMutation.isPending}
          className="text-sm text-red-600 hover:text-red-700 disabled:opacity-50"
        >
          {removeMutation.isPending ? 'Đang xóa...' : 'Xóa'}
        </button>
      </div>
    </div>
  );
}
