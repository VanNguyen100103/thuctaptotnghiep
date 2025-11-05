'use client';

/**
 * Order Details Modal for Admin
 * Full order management: view details, update status, add tracking, add notes
 */

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getOrderById,
  updateOrderStatus,
  updateTrackingNumber,
  updateAdminNotes,
  getAllowedTransitions,
} from '@/lib/api/admin-orders';
import type { Order, OrderStatus } from '@/types/order';

interface OrderDetailsModalProps {
  orderId: number;
  isOpen: boolean;
  onClose: () => void;
}

export default function OrderDetailsModal({ orderId, isOpen, onClose }: OrderDetailsModalProps) {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'details' | 'status' | 'tracking' | 'notes'>('details');
  const [newStatus, setNewStatus] = useState<OrderStatus | ''>('');
  const [trackingNumber, setTrackingNumber] = useState('');
  const [shippingCarrier, setShippingCarrier] = useState('');
  const [adminNotes, setAdminNotes] = useState('');

  // Fetch order details
  const { data: order, isLoading } = useQuery({
    queryKey: ['admin-order', orderId],
    queryFn: () => getOrderById(orderId),
    enabled: isOpen,
  });

  // Fetch allowed transitions
  const { data: transitions } = useQuery({
    queryKey: ['order-transitions', orderId],
    queryFn: () => getAllowedTransitions(orderId),
    enabled: isOpen && activeTab === 'status',
  });

  // Update status mutation
  const statusMutation = useMutation({
    mutationFn: (status: OrderStatus) => updateOrderStatus(orderId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-order', orderId] });
      queryClient.invalidateQueries({ queryKey: ['admin-orders'] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      setNewStatus('');
    },
  });

  // Update tracking mutation
  const trackingMutation = useMutation({
    mutationFn: (data: { trackingNumber: string; shippingCarrier?: string }) =>
      updateTrackingNumber(orderId, data.trackingNumber, data.shippingCarrier),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-order', orderId] });
      queryClient.invalidateQueries({ queryKey: ['admin-orders'] });
      setTrackingNumber('');
      setShippingCarrier('');
    },
  });

  // Update notes mutation
  const notesMutation = useMutation({
    mutationFn: (notes: string) => updateAdminNotes(orderId, notes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-order', orderId] });
      setAdminNotes('');
    },
  });

  const handleStatusUpdate = () => {
    if (newStatus) {
      statusMutation.mutate(newStatus as OrderStatus);
    }
  };

  const handleTrackingUpdate = () => {
    if (trackingNumber.trim()) {
      trackingMutation.mutate({
        trackingNumber: trackingNumber.trim(),
        shippingCarrier: shippingCarrier.trim() || undefined,
      });
    }
  };

  const handleNotesUpdate = () => {
    if (adminNotes.trim()) {
      notesMutation.mutate(adminNotes.trim());
    }
  };

  if (!isOpen) return null;

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'DELIVERED':
        return 'bg-green-100 text-green-800';
      case 'SHIPPED':
      case 'PROCESSING':
        return 'bg-blue-100 text-blue-800';
      case 'PENDING':
      case 'PAYMENT_PENDING':
        return 'bg-yellow-100 text-yellow-800';
      case 'CANCELLED':
      case 'FAILED':
        return 'bg-red-100 text-red-800';
      case 'REFUNDED':
        return 'bg-orange-100 text-orange-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-opacity-50 p-4">
      <div className="w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-lg bg-white shadow-xl">
        {/* Header */}
        <div className="sticky top-0 z-10 flex items-center justify-between border-b bg-white px-6 py-4">
          <div>
            <h2 className="text-xl font-bold text-gray-900">
              Chi Tiết Đơn Hàng
            </h2>
            {order && (
              <p className="text-sm text-gray-500">
                Mã đơn: {order.orderNumber}
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center p-12">
            <svg className="h-8 w-8 animate-spin text-blue-600" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
          </div>
        ) : order ? (
          <>
            {/* Tabs */}
            <div className="border-b px-6">
              <nav className="-mb-px flex space-x-8">
                {['details', 'status', 'tracking', 'notes'].map((tab) => (
                  <button
                    key={tab}
                    onClick={() => setActiveTab(tab as any)}
                    className={`whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium ${
                      activeTab === tab
                        ? 'border-blue-500 text-blue-600'
                        : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700'
                    }`}
                  >
                    {tab === 'details' && 'Chi Tiết'}
                    {tab === 'status' && 'Trạng Thái'}
                    {tab === 'tracking' && 'Vận Chuyển'}
                    {tab === 'notes' && 'Ghi Chú'}
                  </button>
                ))}
              </nav>
            </div>

            {/* Tab Content */}
            <div className="p-6">
              {/* Details Tab */}
              {activeTab === 'details' && (
                <div className="space-y-6">
                  {/* Order Info */}
                  <div>
                    <h3 className="mb-4 text-lg font-semibold">Thông Tin Đơn Hàng</h3>
                    <div className="grid grid-cols-2 gap-4 rounded-lg bg-gray-50 p-4">
                      <div>
                        <p className="text-sm text-gray-600">Trạng thái</p>
                        <span className={`mt-1 inline-flex rounded-full px-3 py-1 text-xs font-semibold ${getStatusColor(order.status)}`}>
                          {order.status}
                        </span>
                      </div>
                      <div>
                        <p className="text-sm text-gray-600">Tổng tiền</p>
                        <p className="font-semibold">{order.total.toLocaleString('vi-VN')} ₫</p>
                      </div>
                      <div>
                        <p className="text-sm text-gray-600">Ngày tạo</p>
                        <p className="font-medium">{new Date(order.createdAt).toLocaleString('vi-VN')}</p>
                      </div>
                      <div>
                        <p className="text-sm text-gray-600">Cập nhật</p>
                        <p className="font-medium">{new Date(order.updatedAt).toLocaleString('vi-VN')}</p>
                      </div>
                    </div>
                  </div>

                  {/* Shipping Address */}
                  <div>
                    <h3 className="mb-4 text-lg font-semibold">Địa Chỉ Giao Hàng</h3>
                    <div className="rounded-lg bg-gray-50 p-4">
                      <p className="font-medium">{order.shippingAddressLine1}</p>
                      {order.shippingAddressLine2 && (
                        <p className="text-gray-600">{order.shippingAddressLine2}</p>
                      )}
                      <p className="text-gray-600">
                        {order.shippingCity}, {order.shippingStateProvince} {order.shippingPostalCode}
                      </p>
                      <p className="text-gray-600">{order.shippingCountry}</p>
                      {order.shippingPhoneNumber && (
                        <p className="mt-2 text-gray-600">SĐT: {order.shippingPhoneNumber}</p>
                      )}
                      <p className="mt-2 text-gray-600">Email: {order.shippingEmail}</p>
                    </div>
                  </div>

                  {/* Order Items */}
                  <div>
                    <h3 className="mb-4 text-lg font-semibold">Sản Phẩm ({order.items.length})</h3>
                    <div className="space-y-3">
                      {order.items.map((item) => (
                        <div key={item.id} className="flex items-center gap-4 rounded-lg border p-3">
                          {item.productImage && (
                            <img
                              src={item.productImage}
                              alt={item.productName}
                              className="h-16 w-16 rounded object-cover"
                            />
                          )}
                          <div className="flex-1">
                            <p className="font-medium">{item.productName}</p>
                            <p className="text-sm text-gray-500">SKU: {item.productSku}</p>
                            {(item.size || item.color) && (
                              <p className="text-sm text-gray-500">
                                {item.size && `Size: ${item.size}`}
                                {item.size && item.color && ' | '}
                                {item.color && `Màu: ${item.color}`}
                              </p>
                            )}
                          </div>
                          <div className="text-right">
                            <p className="font-medium">{item.unitPrice.toLocaleString('vi-VN')} ₫</p>
                            <p className="text-sm text-gray-500">x {item.quantity}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* Status Tab */}
              {activeTab === 'status' && (
                <div className="space-y-4">
                  <div>
                    <h3 className="mb-2 text-lg font-semibold">Cập Nhật Trạng Thái</h3>
                    <p className="text-sm text-gray-600 mb-4">
                      Trạng thái hiện tại: <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${getStatusColor(order.status)}`}>{order.status}</span>
                    </p>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      Chọn trạng thái mới
                    </label>
                    <select
                      value={newStatus}
                      onChange={(e) => setNewStatus(e.target.value as OrderStatus)}
                      className="w-full rounded-lg border border-gray-300 px-4 py-2"
                      disabled={statusMutation.isPending}
                    >
                      <option value="">-- Chọn trạng thái --</option>
                      {transitions?.allowedTransitions.map((status) => (
                        <option key={status} value={status}>
                          {status}
                        </option>
                      ))}
                    </select>
                  </div>

                  {statusMutation.error && (
                    <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800">
                      {(statusMutation.error as any)?.response?.data?.error || 'Có lỗi xảy ra'}
                    </div>
                  )}

                  {statusMutation.isSuccess && (
                    <div className="rounded-lg bg-green-50 p-3 text-sm text-green-800">
                      Cập nhật trạng thái thành công!
                    </div>
                  )}

                  <button
                    onClick={handleStatusUpdate}
                    disabled={!newStatus || statusMutation.isPending}
                    className="w-full rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {statusMutation.isPending ? 'Đang cập nhật...' : 'Cập Nhật Trạng Thái'}
                  </button>
                </div>
              )}

              {/* Tracking Tab */}
              {activeTab === 'tracking' && (
                <div className="space-y-4">
                  <div>
                    <h3 className="mb-2 text-lg font-semibold">Thông Tin Vận Chuyển</h3>
                    {order.trackingNumber && (
                      <div className="mb-4 rounded-lg bg-gray-50 p-4">
                        <p className="text-sm text-gray-600">
                          Mã vận đơn: <span className="font-mono font-semibold">{order.trackingNumber}</span>
                        </p>
                        {order.shippingCarrier && (
                          <p className="text-sm text-gray-600 mt-1">
                            Đơn vị vận chuyển: <span className="font-semibold">{order.shippingCarrier}</span>
                          </p>
                        )}
                      </div>
                    )}
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      Mã vận đơn <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="text"
                      value={trackingNumber}
                      onChange={(e) => setTrackingNumber(e.target.value)}
                      className="w-full rounded-lg border border-gray-300 px-4 py-2"
                      placeholder="VD: VN123456789"
                      disabled={trackingMutation.isPending}
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      Đơn vị vận chuyển
                    </label>
                    <input
                      type="text"
                      value={shippingCarrier}
                      onChange={(e) => setShippingCarrier(e.target.value)}
                      className="w-full rounded-lg border border-gray-300 px-4 py-2"
                      placeholder="VD: Giao Hàng Nhanh, Viettel Post, J&T Express"
                      disabled={trackingMutation.isPending}
                    />
                  </div>

                  {trackingMutation.error && (
                    <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800">
                      {(trackingMutation.error as any)?.response?.data?.error || 'Có lỗi xảy ra'}
                    </div>
                  )}

                  {trackingMutation.isSuccess && (
                    <div className="rounded-lg bg-green-50 p-3 text-sm text-green-800">
                      Cập nhật mã vận đơn thành công!
                    </div>
                  )}

                  <button
                    onClick={handleTrackingUpdate}
                    disabled={!trackingNumber.trim() || trackingMutation.isPending}
                    className="w-full rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {trackingMutation.isPending ? 'Đang cập nhật...' : 'Cập Nhật Mã Vận Đơn'}
                  </button>
                </div>
              )}

              {/* Notes Tab */}
              {activeTab === 'notes' && (
                <div className="space-y-4">
                  <div>
                    <h3 className="mb-2 text-lg font-semibold">Ghi Chú Quản Trị</h3>
                    {order.adminNotes && (
                      <div className="mb-4 rounded-lg bg-gray-50 p-4">
                        <p className="text-sm font-medium text-gray-600 mb-2">Ghi chú hiện tại:</p>
                        <p className="text-sm">{order.adminNotes}</p>
                      </div>
                    )}
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      Thêm ghi chú mới
                    </label>
                    <textarea
                      value={adminNotes}
                      onChange={(e) => setAdminNotes(e.target.value)}
                      rows={4}
                      className="w-full rounded-lg border border-gray-300 px-4 py-2"
                      placeholder="Nhập ghi chú cho đơn hàng này..."
                      disabled={notesMutation.isPending}
                    />
                  </div>

                  {notesMutation.error && (
                    <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800">
                      {(notesMutation.error as any)?.response?.data?.error || 'Có lỗi xảy ra'}
                    </div>
                  )}

                  {notesMutation.isSuccess && (
                    <div className="rounded-lg bg-green-50 p-3 text-sm text-green-800">
                      Cập nhật ghi chú thành công!
                    </div>
                  )}

                  <button
                    onClick={handleNotesUpdate}
                    disabled={!adminNotes.trim() || notesMutation.isPending}
                    className="w-full rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {notesMutation.isPending ? 'Đang cập nhật...' : 'Thêm Ghi Chú'}
                  </button>
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="p-12 text-center text-gray-500">
            Không tìm thấy đơn hàng
          </div>
        )}
      </div>
    </div>
  );
}
