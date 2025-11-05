/**
 * Order Detail Page
 * Shows detailed information about a specific order
 */

'use client';

import { useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/contexts/AuthContext';
import { getOrderById, cancelOrder } from '@/lib/api/orders';
import { getPaymentByOrderId, createPayment } from '@/lib/api/payments';
import Link from 'next/link';
import type { OrderStatus } from '@/types/order';

const STATUS_COLORS: Record<OrderStatus, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  PAYMENT_PENDING: 'bg-orange-100 text-orange-800',
  PAID: 'bg-green-100 text-green-800',
  PROCESSING: 'bg-blue-100 text-blue-800',
  SHIPPED: 'bg-indigo-100 text-indigo-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
  REFUNDED: 'bg-purple-100 text-purple-800',
  FAILED: 'bg-red-100 text-red-800',
};

const STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Chờ thanh toán',
  PAYMENT_PENDING: 'Đang xử lý thanh toán',
  PAID: 'Đã thanh toán',
  PROCESSING: 'Đang xử lý',
  SHIPPED: 'Đang giao hàng',
  DELIVERED: 'Đã giao hàng',
  CANCELLED: 'Đã hủy',
  REFUNDED: 'Đã hoàn tiền',
  FAILED: 'Thất bại',
};

export default function OrderDetailPage() {
  const router = useRouter();
  const params = useParams();
  const queryClient = useQueryClient();
  const { isAuthenticated, loading: authLoading } = useAuth();
  const orderId = params.id ? parseInt(params.id as string) : null;

  // Redirect if not authenticated
  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.push('/login?redirect=/orders');
    }
  }, [isAuthenticated, authLoading, router]);

  // Fetch order details
  const { data: order, isLoading: orderLoading } = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => getOrderById(orderId!),
    enabled: isAuthenticated && orderId !== null,
  });

  // Fetch payment details
  const { data: payment } = useQuery({
    queryKey: ['payment', orderId],
    queryFn: () => getPaymentByOrderId(orderId!),
    enabled: isAuthenticated && orderId !== null,
  });

  // Cancel order mutation
  const cancelOrderMutation = useMutation({
    mutationFn: () => cancelOrder(orderId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['order', orderId] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      alert('Đơn hàng đã được hủy thành công');
    },
    onError: (error: any) => {
      alert(`Lỗi khi hủy đơn hàng: $
        {error.message}`);
    },
  });

  // Retry payment mutation
  const retryPaymentMutation = useMutation({
    mutationFn: () => createPayment({ orderId: orderId! }),
    onSuccess: (response) => {
      // Redirect to PayPal
      window.location.href = response.approvalUrl;
    },
    onError: (error: any) => {
      alert(`Lỗi khi tạo thanh toán: ${error.message}`);
    },
  });

  const handleCancelOrder = () => {
    if (confirm('Bạn có chắc muốn hủy đơn hàng này?')) {
      cancelOrderMutation.mutate();
    }
  };

  const handleRetryPayment = () => {
    retryPaymentMutation.mutate();
  };

  if (authLoading || orderLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (!isAuthenticated || !order) {
    return null;
  }

  const canCancel = order.status === 'PENDING';
  const canPay = order.status === 'PENDING' && (!payment || 'hasPayment' in payment);

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-6">
          <Link
            href="/orders"
            className="text-blue-600 hover:text-blue-700 flex items-center gap-2 mb-4"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
            Quay Lại Đơn Hàng
          </Link>
          <div className="flex items-start justify-between">
            <div>
              <h1 className="text-3xl font-bold text-gray-900 mb-2">
                Order #{order.orderNumber}
              </h1>
              <p className="text-gray-600">
                Đặt lúc {new Date(order.createdAt).toLocaleDateString('vi-VN', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </p>
            </div>
            <span
              className={`px-4 py-2 rounded-full text-sm font-semibold ${
                STATUS_COLORS[order.status]
              }`}
            >
              {STATUS_LABELS[order.status]}
            </span>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Main Content */}
          <div className="lg:col-span-2 space-y-6">
            {/* Order Items */}
            <div className="bg-white rounded-lg shadow-sm p-6">
              <h2 className="text-xl font-semibold mb-4">Sản Phẩm Đặt Hàng</h2>
              <div className="space-y-4">
                {order.items.map((item) => (
                  <div key={item.id} className="flex gap-4 pb-4 border-b last:border-b-0">
                    <img
                      src={item.productImage || 'https://placehold.co/80x80?text=No+Image'}
                      alt={item.productName}
                      className="w-20 h-20 object-cover rounded-lg"
                    />
                    <div className="flex-1">
                      <h3 className="font-medium text-gray-900">{item.productName}</h3>
                      <p className="text-sm text-gray-600">SKU: {item.productSku}</p>
                      {item.size && <p className="text-sm text-gray-600">Size: {item.size}</p>}
                      {item.color && <p className="text-sm text-gray-600">Color: {item.color}</p>}
                      <p className="text-sm text-gray-600">Số lượng: {item.quantity}</p>
                    </div>
                    <div className="text-right">
                      <p className="font-semibold">
                        {item.subtotal.toLocaleString('vi-VN')} ₫
                      </p>
                      <p className="text-sm text-gray-600">
                        {item.unitPrice.toLocaleString('vi-VN')} ₫ /sp
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Shipping Address */}
            <div className="bg-white rounded-lg shadow-sm p-6">
              <h2 className="text-xl font-semibold mb-4">Địa Chỉ Giao Hàng</h2>
              <div className="text-gray-700">
                <p>{order.shippingAddressLine1}</p>
                {order.shippingAddressLine2 && (
                  <p>{order.shippingAddressLine2}</p>
                )}
                <p>
                  {order.shippingCity}, {order.shippingStateProvince}{' '}
                  {order.shippingPostalCode}
                </p>
                <p>{order.shippingCountry}</p>
                {order.shippingPhoneNumber && (
                  <p className="mt-2">Phone: {order.shippingPhoneNumber}</p>
                )}
                <p className="mt-2">Email: {order.shippingEmail}</p>
              </div>
            </div>

            {/* Payment Information */}
            {payment && 'id' in payment && (
              <div className="bg-white rounded-lg shadow-sm p-6">
                <h2 className="text-xl font-semibold mb-4">Thông Tin Thanh Toán</h2>
                <div className="space-y-2">
                  <div className="flex justify-between">
                    <span className="text-gray-600">Phương Thức Thanh Toán:</span>
                    <span className="font-medium">{payment.paymentMethod}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Trạng Thái Thanh Toán:</span>
                    <span className="font-medium">{payment.status}</span>
                  </div>
                  {payment.transactionId && (
                    <div className="flex justify-between">
                      <span className="text-gray-600">Mã Giao Dịch:</span>
                      <span className="font-mono text-sm">{payment.transactionId}</span>
                    </div>
                  )}
                  {payment.paymentDate && (
                    <div className="flex justify-between">
                      <span className="text-gray-600">Ngày Thanh Toán:</span>
                      <span>
                        {new Date(payment.paymentDate).toLocaleDateString('vi-VN', {
                          year: 'numeric',
                          month: 'long',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </span>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* Sidebar - Order Summary */}
          <div>
            <div className="bg-white rounded-lg shadow-sm p-6 top-4">
              <h2 className="text-xl font-semibold mb-4">Tóm Tắt Đơn Hàng</h2>

              <div className="space-y-2 mb-4">
                <div className="flex justify-between">
                  <span className="text-gray-600">Tổng Phụ</span>
                  <span>{order.subtotal.toLocaleString('vi-VN')} ₫</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Vận Chuyển</span>
                  <span>
                    {order.shippingCost === 0 ? (
                      <span className="text-green-600">MIỄN PHÍ</span>
                    ) : (
                      `${order.shippingCost.toLocaleString('vi-VN')} ₫`
                    )}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Thuế</span>
                  <span>{order.taxAmount.toLocaleString('vi-VN')} ₫</span>
                </div>
                {order.discountAmount > 0 && (
                  <div className="flex justify-between text-green-600">
                    <span>Giảm Giá</span>
                    <span>-{order.discountAmount.toLocaleString('vi-VN')} ₫</span>
                  </div>
                )}
                <div className="border-t pt-2 flex justify-between font-bold text-lg">
                  <span>Tổng Cộng</span>
                  <span className="text-blue-600">{order.total.toLocaleString('vi-VN')} ₫</span>
                </div>
              </div>

              {/* Actions */}
              <div className="space-y-3">
                {canPay && (
                  <button
                    onClick={handleRetryPayment}
                    disabled={retryPaymentMutation.isPending}
                    className="w-full px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-semibold disabled:opacity-50"
                  >
                    {retryPaymentMutation.isPending ? 'Đang xử lý...' : 'Thanh Toán Ngay'}
                  </button>
                )}
                {canCancel && (
                  <button
                    onClick={handleCancelOrder}
                    disabled={cancelOrderMutation.isPending}
                    className="w-full px-6 py-3 border border-red-300 text-red-600 rounded-lg hover:bg-red-50 font-semibold disabled:opacity-50"
                  >
                    {cancelOrderMutation.isPending ? 'Đang hủy...' : 'Hủy Đơn Hàng'}
                  </button>
                )}
                {order.trackingNumber && (
                  <div className="p-4 bg-blue-50 rounded-lg">
                    <p className="text-sm text-gray-600 mb-1">Mã Vận Đơn</p>
                    <p className="font-mono text-sm font-semibold">{order.trackingNumber}</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
