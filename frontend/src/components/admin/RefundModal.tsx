'use client';

/**
 * Refund Modal Component
 * Allows admin to process full or partial refunds via PayPal
 */

import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { refundPayment } from '@/lib/api/payments';
import type { Payment } from '@/types/payment';

interface RefundModalProps {
  payment: Payment;
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

// Exchange rate: USD to VND
const USD_TO_VND_RATE = 25000;

export default function RefundModal({ payment, isOpen, onClose, onSuccess }: RefundModalProps) {
  const queryClient = useQueryClient();
  const [refundAmount, setRefundAmount] = useState<string>('');
  const [reason, setReason] = useState<string>('');
  const [error, setError] = useState<string>('');

  // Calculate maximum refundable amount
  const maxRefundAmount = payment.amount - payment.refundAmount;
  const isFullRefund = parseFloat(refundAmount || '0') === maxRefundAmount;

  // Refund mutation
  const refundMutation = useMutation({
    mutationFn: async () => {
      const amount = parseFloat(refundAmount);
      if (isNaN(amount) || amount <= 0) {
        throw new Error('Số tiền hoàn không hợp lệ');
      }
      if (amount > maxRefundAmount) {
        throw new Error(`Số tiền hoàn không được vượt quá ${maxRefundAmount.toLocaleString('vi-VN')} ₫`);
      }
      return refundPayment(payment.id, { amount, reason });
    },
    onSuccess: (data) => {
      // Invalidate related queries
      queryClient.invalidateQueries({ queryKey: ['payment', payment.id] });
      queryClient.invalidateQueries({ queryKey: ['order', payment.orderId] });
      queryClient.invalidateQueries({ queryKey: ['payments'] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });

      // Reset form
      setRefundAmount('');
      setReason('');
      setError('');

      // Call success callback and close modal
      onSuccess?.();
      onClose();
    },
    onError: (err: any) => {
      const errorMessage = err?.response?.data?.error || err?.message || 'Có lỗi xảy ra khi hoàn tiền';
      setError(errorMessage);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    refundMutation.mutate();
  };

  const handleSetFullRefund = () => {
    setRefundAmount(maxRefundAmount.toString());
  };

  if (!isOpen) return null;

  // Check if payment can be refunded
  const canRefund = payment.status === 'COMPLETED' || payment.status === 'PARTIALLY_REFUNDED';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center  bg-opacity-50 p-4">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-xl font-bold text-gray-900">Hoàn Tiền Thanh Toán</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
            disabled={refundMutation.isPending}
          >
            <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Payment Info */}
        <div className="mb-6 rounded-lg bg-gray-50 p-4">
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">Mã đơn hàng:</span>
              <span className="font-medium">{payment.orderNumber}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Tổng thanh toán:</span>
              <span className="font-medium">
                {payment.amount.toFixed(2)} đ
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Đã hoàn:</span>
              <span className="font-medium text-orange-600">
                ${payment.refundAmount.toFixed(2)} ({(payment.refundAmount * USD_TO_VND_RATE).toLocaleString('vi-VN')} ₫)
              </span>
            </div>
            <div className="flex justify-between border-t border-gray-200 pt-2">
              <span className="font-semibold text-gray-900">Có thể hoàn:</span>
              <span className="font-semibold text-green-600">
                {maxRefundAmount.toFixed(2)} đ
              </span>
            </div>
          </div>
        </div>

        {!canRefund ? (
          <div className="mb-4 rounded-lg bg-red-50 p-4 text-sm text-red-800">
            Thanh toán này không thể hoàn tiền. Chỉ các thanh toán đã hoàn thành mới có thể hoàn tiền.
          </div>
        ) : maxRefundAmount <= 0 ? (
          <div className="mb-4 rounded-lg bg-yellow-50 p-4 text-sm text-yellow-800">
            Thanh toán này đã được hoàn toàn bộ số tiền.
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Refund Amount */}
            <div>
              <label htmlFor="refundAmount" className="mb-1 block text-sm font-medium text-gray-700">
                Số tiền hoàn <span className="text-red-500">*</span>
              </label>
              <div className="space-y-2">
                <input
                  type="number"
                  id="refundAmount"
                  value={refundAmount}
                  onChange={(e) => setRefundAmount(e.target.value)}
                  min="0.01"
                  max={maxRefundAmount}
                  step="0.01"
                  required
                  disabled={refundMutation.isPending}
                  className="w-full rounded-lg border border-gray-300 px-4 py-2 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
                  placeholder="Nhập số tiền USD cần hoàn (VD: 9.87)"
                />
                <button
                  type="button"
                  onClick={handleSetFullRefund}
                  disabled={refundMutation.isPending}
                  className="text-sm text-blue-600 hover:text-blue-700 disabled:text-gray-400"
                >
                  Hoàn toàn bộ: {maxRefundAmount.toFixed(2)} đ
                </button>
              </div>
            </div>

            {/* Reason */}
            <div>
              <label htmlFor="reason" className="mb-1 block text-sm font-medium text-gray-700">
                Lý do hoàn tiền
              </label>
              <textarea
                id="reason"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={3}
                disabled={refundMutation.isPending}
                className="w-full rounded-lg border border-gray-300 px-4 py-2 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
                placeholder="Nhập lý do hoàn tiền (tùy chọn)"
              />
            </div>

            {/* Refund Type Info */}
            {refundAmount && (
              <div className={`rounded-lg p-3 text-sm ${isFullRefund ? 'bg-orange-50 text-orange-800' : 'bg-blue-50 text-blue-800'}`}>
                {isFullRefund ? (
                  <>
                    <strong>Hoàn toàn bộ:</strong> Đơn hàng sẽ được đánh dấu là HỦY BỎ sau khi hoàn tiền.
                  </>
                ) : (
                  <>
                    <strong>Hoàn một phần:</strong> Thanh toán sẽ được đánh dấu là ĐÃ HOÀN MỘT PHẦN.
                  </>
                )}
              </div>
            )}

            {/* Error Message */}
            {error && (
              <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800">
                {error}
              </div>
            )}

            {/* Action Buttons */}
            <div className="flex gap-3 pt-2">
              <button
                type="button"
                onClick={onClose}
                disabled={refundMutation.isPending}
                className="flex-1 rounded-lg border border-gray-300 px-4 py-2 text-gray-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Hủy
              </button>
              <button
                type="submit"
                disabled={refundMutation.isPending || !refundAmount}
                className="flex-1 rounded-lg bg-blue-600 px-4 py-2 text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {refundMutation.isPending ? (
                  <span className="flex items-center justify-center gap-2">
                    <svg className="h-5 w-5 animate-spin" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    Đang xử lý...
                  </span>
                ) : (
                  'Xác nhận hoàn tiền'
                )}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
