'use client';

/**
 * Admin Orders Management Page
 * Displays all orders with payment information and refund capability
 */

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { get } from '@/lib/api/client';
import { getPaymentByOrderId } from '@/lib/api/payments';
import { searchOrders } from '@/lib/api/admin-orders';
import { useDebounce } from '@/hooks/useDebounce';
import RefundModal from '@/components/admin/RefundModal';
import OrderDetailsModal from '@/components/admin/OrderDetailsModal';
import type { OrderSummary } from '@/types/order';
import type { Payment } from '@/types/payment';

interface OrderWithPayment extends OrderSummary {
  payment?: Payment | null;
}

// Exchange rate: USD to VND
const USD_TO_VND_RATE = 25000;

export default function AdminOrdersPage() {
  const [page, setPage] = useState(0);
  const [selectedPayment, setSelectedPayment] = useState<Payment | null>(null);
  const [refundModalOpen, setRefundModalOpen] = useState(false);
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null);
  const [detailsModalOpen, setDetailsModalOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Debounce search query to avoid excessive API calls
  const debouncedSearch = useDebounce(searchQuery, 500);

  // Fetch all orders (admin endpoint) or search results
  const { data: ordersData, isLoading } = useQuery({
    queryKey: ['admin-orders', page, debouncedSearch],
    queryFn: async () => {
      let response: {
        orders: OrderSummary[];
        totalPages: number;
        totalOrders?: number;
        totalItems?: number;
        currentPage: number;
      };

      // Use search endpoint if query exists, otherwise get all orders
      if (debouncedSearch.trim()) {
        const searchResponse = await searchOrders(debouncedSearch, { page, size: 20 });
        // Convert Order[] to OrderSummary[]
        const orderSummaries: OrderSummary[] = searchResponse.orders.map(order => ({
          id: order.id,
          orderNumber: order.orderNumber,
          status: order.status,
          total: order.total,
          itemCount: order.items?.length || 0,
          createdAt: order.createdAt,
          trackingNumber: order.trackingNumber,
          shippingCarrier: order.shippingCarrier,
        }));
        response = {
          orders: orderSummaries,
          totalPages: searchResponse.totalPages,
          totalItems: searchResponse.totalItems,
          currentPage: searchResponse.currentPage,
        };
      } else {
        const allOrdersResponse = await get<{
          orders: OrderSummary[];
          totalPages: number;
          totalOrders: number;
          currentPage: number;
        }>(`/api/orders?page=${page}&size=4`);
        response = allOrdersResponse;
      }

      // Fetch payment for each order
      const ordersWithPayments = await Promise.all(
        response.orders.map(async (order) => {
          try {
            const payment = await getPaymentByOrderId(order.id);
            // Check if payment exists (not the "no payment" message)
            if (payment && 'id' in payment) {
              return { ...order, payment };
            }
          } catch (err) {
            console.log(`No payment found for order ${order.id}`);
          }
          return { ...order, payment: null };
        })
      );

      return {
        orders: ordersWithPayments,
        totalPages: response.totalPages,
        totalOrders: response.totalOrders || response.totalItems || 0,
        currentPage: response.currentPage,
      };
    },
  });

  const handleRefundClick = (payment: Payment) => {
    setSelectedPayment(payment);
    setRefundModalOpen(true);
  };

  const handleDetailsClick = (orderId: number) => {
    setSelectedOrderId(orderId);
    setDetailsModalOpen(true);
  };

  const getStatusBadgeColor = (status: string) => {
    switch (status) {
      case 'PAID':
      case 'DELIVERED':
        return 'bg-green-100 text-green-800';
      case 'PENDING':
        return 'bg-yellow-100 text-yellow-800';
      case 'PROCESSING':
      case 'SHIPPED':
        return 'bg-blue-100 text-blue-800';
      case 'CANCELLED':
      case 'FAILED':
        return 'bg-red-100 text-red-800';
      case 'REFUNDED':
        return 'bg-orange-100 text-orange-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getPaymentStatusBadgeColor = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'bg-green-100 text-green-800';
      case 'PENDING':
        return 'bg-yellow-100 text-yellow-800';
      case 'FAILED':
        return 'bg-red-100 text-red-800';
      case 'REFUNDED':
        return 'bg-orange-100 text-orange-800';
      case 'PARTIALLY_REFUNDED':
        return 'bg-purple-100 text-purple-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const canRefund = (payment: Payment | null) => {
    if (!payment) return false;
    const refundableStatuses = ['COMPLETED', 'PARTIALLY_REFUNDED'];
    const hasRefundableAmount = (payment.amount - payment.refundAmount) > 0;
    return refundableStatuses.includes(payment.status) && hasRefundableAmount;
  };

  // Export to CSV
  const handleExportCSV = () => {
    if (!ordersData || ordersData.orders.length === 0) {
      alert('Không có dữ liệu để xuất');
      return;
    }

    // Helper function to escape CSV values
    const escapeCsvValue = (value: string | number): string => {
      const stringValue = String(value);
      // If value contains comma, quote, or newline, wrap in quotes and escape quotes
      if (stringValue.includes(',') || stringValue.includes('"') || stringValue.includes('\n')) {
        return `"${stringValue.replace(/"/g, '""')}"`;
      }
      return stringValue;
    };

    // CSV Headers
    const headers = [
      'Mã đơn',
      'Số lượng SP',
      'Tổng tiền (đ)',
      'Trạng thái đơn',
      'Trạng thái thanh toán',
      'Số tiền đã hoàn (đ)',
      'Ngày tạo',
      'Mã vận đơn',
      'Đơn vị vận chuyển'
    ];

    // Convert orders to CSV rows
    const rows = ordersData.orders.map((order) => {
      const payment = order.payment;
      const refundAmount = payment && payment.refundAmount > 0
        ? (payment.currency === 'USD'
            ? (payment.refundAmount * USD_TO_VND_RATE).toFixed(0)
            : payment.refundAmount.toFixed(0))
        : '0';

      return [
        escapeCsvValue(order.orderNumber),
        escapeCsvValue(order.itemCount),
        escapeCsvValue(order.total.toFixed(0)),
        escapeCsvValue(order.status),
        escapeCsvValue(payment ? payment.status : 'Chưa thanh toán'),
        escapeCsvValue(refundAmount),
        escapeCsvValue(new Date(order.createdAt).toLocaleString('vi-VN')),
        escapeCsvValue(order.trackingNumber || ''),
        escapeCsvValue(order.shippingCarrier || '')
      ];
    });

    // Combine headers and rows
    const csvContent = [
      headers.map(h => escapeCsvValue(h)).join(','),
      ...rows.map(row => row.join(','))
    ].join('\n');

    // Add BOM for UTF-8 encoding (fixes Vietnamese characters in Excel)
    const BOM = '\uFEFF';
    const blob = new Blob([BOM + csvContent], { type: 'text/csv;charset=utf-8;' });

    // Create download link
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);

    // Generate filename with timestamp
    const timestamp = new Date().toISOString().split('T')[0];
    const filename = debouncedSearch
      ? `orders-search-${debouncedSearch}-${timestamp}.csv`
      : `orders-all-${timestamp}.csv`;

    link.setAttribute('download', filename);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Quản Lý Đơn Hàng</h1>
          <p className="mt-1 text-sm text-gray-500">
            Xem và quản lý tất cả đơn hàng, thanh toán và hoàn tiền
          </p>
        </div>
        <button
          onClick={handleExportCSV}
          disabled={isLoading || !ordersData || ordersData.orders.length === 0}
          className="flex items-center gap-2 rounded-lg bg-green-600 px-4 py-2 text-white transition-colors hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          Xuất CSV
        </button>
      </div>

      {/* Search Box */}
      <div className="bg-white rounded-lg shadow p-4">
        <div className="relative">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setPage(0); // Reset to first page on new search
            }}
            placeholder="Tìm kiếm theo mã đơn hàng (VD: ORD-1761249)..."
            className="w-full rounded-lg border border-gray-300 px-4 py-2 pl-10 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <svg
            className="absolute left-3 top-2.5 h-5 w-5 text-gray-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
          {searchQuery && (
            <button
              onClick={() => {
                setSearchQuery('');
                setPage(0);
              }}
              className="absolute right-3 top-2.5 text-gray-400 hover:text-gray-600"
            >
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>
        {debouncedSearch && (
          <p className="mt-2 text-sm text-gray-600">
            Đang tìm kiếm: <span className="font-semibold">{debouncedSearch}</span>
          </p>
        )}
      </div>

      {/* Orders Table */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Mã đơn
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Số lượng
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Tổng tiền
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Trạng thái
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Thanh toán
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Hoàn tiền
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Thao tác
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 bg-white">
              {isLoading ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-sm text-gray-500">
                    <div className="flex items-center justify-center gap-2">
                      <svg className="h-5 w-5 animate-spin" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                      </svg>
                      Đang tải...
                    </div>
                  </td>
                </tr>
              ) : ordersData?.orders.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-sm text-gray-500">
                    Không có đơn hàng nào
                  </td>
                </tr>
              ) : (
                ordersData?.orders.map((order: OrderWithPayment) => (
                  <tr key={order.id} className="hover:bg-gray-50">
                    <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-gray-900">
                      {order.orderNumber}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-900">
                      <div className="text-xs text-gray-500">
                        {order.itemCount} sản phẩm
                      </div>
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900">
                      {order.total.toLocaleString('vi-VN')} ₫
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-sm">
                      <span className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ${getStatusBadgeColor(order.status)}`}>
                        {order.status}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-sm">
                      {order.payment ? (
                        <span className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ${getPaymentStatusBadgeColor(order.payment.status)}`}>
                          {order.payment.status}
                        </span>
                      ) : (
                        <span className="text-gray-400 text-xs">Chưa thanh toán</span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-900">
                      {order.payment && order.payment.refundAmount > 0 ? (
                        <div className="space-y-1">
                          <div className="text-orange-600 font-medium">
                            {order.payment.currency === 'USD'
                              ? `${(order.payment.refundAmount * USD_TO_VND_RATE).toLocaleString('vi-VN')} ₫`
                              : `${order.payment.refundAmount.toLocaleString('vi-VN')} ₫`
                            }
                          </div>
                          {order.payment.status === 'PARTIALLY_REFUNDED' && (
                            <div className="text-xs text-gray-500">
                              Còn: {order.payment.currency === 'USD'
                                ? `${(order.total - (order.payment.refundAmount * USD_TO_VND_RATE)).toLocaleString('vi-VN')} ₫`
                                : `${(order.payment.amount - order.payment.refundAmount).toLocaleString('vi-VN')} ₫`
                              }
                            </div>
                          )}
                        </div>
                      ) : (
                        <span className="text-gray-400 text-xs">-</span>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-sm">
                      <div className="flex gap-2">
                        <button
                          onClick={() => handleDetailsClick(order.id)}
                          className="rounded bg-gray-600 px-3 py-1 text-xs font-medium text-white transition-colors hover:bg-gray-700"
                        >
                          Chi Tiết
                        </button>
                        {canRefund(order.payment ?? null) && order.payment && (
                          <button
                            onClick={() => handleRefundClick(order.payment!)}
                            className="rounded bg-blue-600 px-3 py-1 text-xs font-medium text-white transition-colors hover:bg-blue-700"
                          >
                            Hoàn tiền
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {ordersData && ordersData.orders.length > 0 && (
          <div className="flex items-center justify-between border-t border-gray-200 bg-white px-4 py-3 sm:px-6">
            <div className="flex flex-1 justify-between sm:hidden">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="relative inline-flex items-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Trước
              </button>
              <button
                onClick={() => setPage(Math.min(ordersData.totalPages - 1, page + 1))}
                disabled={page >= ordersData.totalPages - 1}
                className="relative ml-3 inline-flex items-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Sau
              </button>
            </div>
            <div className="hidden sm:flex sm:flex-1 sm:items-center sm:justify-between">
              <div>
                <p className="text-sm text-gray-700">
                  Hiển thị <span className="font-medium">{page * 20 + 1}</span> đến{' '}
                  <span className="font-medium">
                    {Math.min((page + 1) * 20, ordersData.totalOrders)}
                  </span>{' '}
                  trong tổng số <span className="font-medium">{ordersData.totalOrders}</span> đơn hàng
                  {debouncedSearch && <span className="text-gray-500"> (đã lọc)</span>}
                </p>
              </div>
              <div>
                <nav className="isolate inline-flex -space-x-px rounded-md shadow-sm">
                  <button
                    onClick={() => setPage(Math.max(0, page - 1))}
                    disabled={page === 0}
                    className="relative inline-flex items-center rounded-l-md border border-gray-300 bg-white px-2 py-2 text-sm font-medium text-gray-500 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <span className="sr-only">Trước</span>
                    <svg className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M12.79 5.23a.75.75 0 01-.02 1.06L8.832 10l3.938 3.71a.75.75 0 11-1.04 1.08l-4.5-4.25a.75.75 0 010-1.08l4.5-4.25a.75.75 0 011.06.02z" clipRule="evenodd" />
                    </svg>
                  </button>
                  <span className="relative inline-flex items-center border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700">
                    Trang {page + 1} / {ordersData.totalPages}
                  </span>
                  <button
                    onClick={() => setPage(Math.min(ordersData.totalPages - 1, page + 1))}
                    disabled={page >= ordersData.totalPages - 1}
                    className="relative inline-flex items-center rounded-r-md border border-gray-300 bg-white px-2 py-2 text-sm font-medium text-gray-500 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <span className="sr-only">Sau</span>
                    <svg className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clipRule="evenodd" />
                    </svg>
                  </button>
                </nav>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Refund Modal */}
      {selectedPayment && (
        <RefundModal
          payment={selectedPayment}
          isOpen={refundModalOpen}
          onClose={() => {
            setRefundModalOpen(false);
            setSelectedPayment(null);
          }}
          onSuccess={() => {
            // Refetch orders after successful refund
            // The query will be invalidated by RefundModal
          }}
        />
      )}

      {/* Order Details Modal */}
      {selectedOrderId && (
        <OrderDetailsModal
          orderId={selectedOrderId}
          isOpen={detailsModalOpen}
          onClose={() => {
            setDetailsModalOpen(false);
            setSelectedOrderId(null);
          }}
        />
      )}
    </div>
  );
}
