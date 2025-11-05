'use client';

/**
 * Order Clustering Analytics Page
 * Uses CSR (Client-Side Rendering) for real-time data fetching
 */

import { useEffect, useState } from 'react';
import { getOrderClusters, OrderClusteringResponse, OrderMetrics } from '@/lib/api/clustering';
import { ApiError } from '@/lib/api/client';
import { cleanAIText } from '@/utils/textUtils';

// Define cluster types based on order value
type ClusterType = 'lowValue' | 'mediumValue' | 'highValue' | 'premium';

interface ClusteredOrder extends OrderMetrics {
  cluster: ClusterType;
}

// Cluster boundaries (matching backend rules)
const CLUSTER_RULES = {
  lowValue: { max: 200000, label: 'Giá Trị Thấp', color: 'bg-red-100 text-red-800' },
  mediumValue: { min: 200000, max: 500000, label: 'Giá Trị Trung Bình', color: 'bg-yellow-100 text-yellow-800' },
  highValue: { min: 500000, max: 1000000, label: 'Giá Trị Cao', color: 'bg-blue-100 text-blue-800' },
  premium: { min: 1000000, label: 'Cao Cấp', color: 'bg-green-100 text-green-800' },
};

// Status colors with Vietnamese labels
const STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-gray-100 text-gray-800',
  PROCESSING: 'bg-blue-100 text-blue-800',
  SHIPPED: 'bg-purple-100 text-purple-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
  REFUNDED: 'bg-orange-100 text-orange-800',
  PAYMENT_PENDING: 'bg-yellow-100 text-yellow-800',
  PAID: 'bg-green-100 text-green-800',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Chờ Xử Lý',
  PROCESSING: 'Đang Xử Lý',
  SHIPPED: 'Đã Gửi Hàng',
  DELIVERED: 'Đã Giao',
  CANCELLED: 'Đã Hủy',
  REFUNDED: 'Đã Hoàn Tiền',
  PAYMENT_PENDING: 'Chờ Thanh Toán',
  PAID: 'Đã Thanh Toán',
};

function determineCluster(totalAmount: number): ClusterType {
  if (totalAmount < 200000) return 'lowValue';
  if (totalAmount < 500000) return 'mediumValue';
  if (totalAmount < 1000000) return 'highValue';
  return 'premium';
}

export default function OrderClusteringPage() {
  const [data, setData] = useState<OrderClusteringResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedCluster, setSelectedCluster] = useState<ClusterType | 'all'>('all');

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const response = await getOrderClusters();
        setData(response);
        setError(null);
      } catch (err) {
        const apiError = err as ApiError;
        setError(apiError.message || 'Failed to fetch order clustering data');
        console.error('Error fetching order clusters:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
          <p className="mt-4 text-gray-600">Đang tải dữ liệu phân cụm đơn hàng...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-6">
        <h3 className="text-lg font-semibold text-red-800 mb-2">Lỗi Tải Dữ Liệu</h3>
        <p className="text-red-600">{error}</p>
        <button
          onClick={() => window.location.reload()}
          className="mt-4 bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700"
        >
          Thử Lại
        </button>
      </div>
    );
  }

  if (!data || !data.totalOrders) {
    return (
      <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-6">
        <p className="text-yellow-800">Không có dữ liệu phân cụm</p>
      </div>
    );
  }

  // Cluster orders
  const clusteredOrders: ClusteredOrder[] = data.rawData.map(order => ({
    ...order,
    cluster: determineCluster(order.totalAmount),
  }));

  // Calculate cluster statistics
  const clusterStats = {
    lowValue: clusteredOrders.filter(o => o.cluster === 'lowValue'),
    mediumValue: clusteredOrders.filter(o => o.cluster === 'mediumValue'),
    highValue: clusteredOrders.filter(o => o.cluster === 'highValue'),
    premium: clusteredOrders.filter(o => o.cluster === 'premium'),
  };

  // Calculate total revenue per cluster
  const clusterRevenue = {
    lowValue: clusterStats.lowValue.reduce((sum, o) => sum + o.totalAmount, 0),
    mediumValue: clusterStats.mediumValue.reduce((sum, o) => sum + o.totalAmount, 0),
    highValue: clusterStats.highValue.reduce((sum, o) => sum + o.totalAmount, 0),
    premium: clusterStats.premium.reduce((sum, o) => sum + o.totalAmount, 0),
  };

  const filteredOrders = selectedCluster === 'all'
    ? clusteredOrders
    : clusterStats[selectedCluster];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white shadow rounded-lg p-6">
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Phân Tích Phân Cụm Đơn Hàng</h2>
        <p className="text-gray-600">
          Phân cụm đơn hàng thông minh dựa trên giá trị và mẫu đơn hàng bằng AI
        </p>
        <div className="mt-4 flex items-center text-sm text-gray-500">
          <svg className="h-5 w-5 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          Cập nhật lần cuối: {new Date(data.timestamp).toLocaleString('vi-VN')}
        </div>
      </div>

      {/* Cluster Summary Cards */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <div className="bg-white shadow rounded-lg p-6 border-l-4 border-red-500">
          <h3 className="text-sm font-medium text-gray-500">Đơn Hàng Giá Trị Thấp</h3>
          <p className="mt-2 text-3xl font-bold text-gray-900">{clusterStats.lowValue.length}</p>
          <p className="mt-1 text-xs text-gray-600">&lt; 200,000 VND</p>
          <p className="mt-2 text-sm font-semibold text-gray-700">
            Doanh thu: {clusterRevenue.lowValue.toLocaleString('vi-VN')} VND
          </p>
        </div>
        <div className="bg-white shadow rounded-lg p-6 border-l-4 border-yellow-500">
          <h3 className="text-sm font-medium text-gray-500">Đơn Hàng Giá Trị Trung Bình</h3>
          <p className="mt-2 text-3xl font-bold text-gray-900">{clusterStats.mediumValue.length}</p>
          <p className="mt-1 text-xs text-gray-600">200K - 500K VND</p>
          <p className="mt-2 text-sm font-semibold text-gray-700">
            Doanh thu: {clusterRevenue.mediumValue.toLocaleString('vi-VN')} VND
          </p>
        </div>
        <div className="bg-white shadow rounded-lg p-6 border-l-4 border-blue-500">
          <h3 className="text-sm font-medium text-gray-500">Đơn Hàng Giá Trị Cao</h3>
          <p className="mt-2 text-3xl font-bold text-gray-900">{clusterStats.highValue.length}</p>
          <p className="mt-1 text-xs text-gray-600">500K - 1M VND</p>
          <p className="mt-2 text-sm font-semibold text-gray-700">
            Doanh thu: {clusterRevenue.highValue.toLocaleString('vi-VN')} VND
          </p>
        </div>
        <div className="bg-white shadow rounded-lg p-6 border-l-4 border-green-500">
          <h3 className="text-sm font-medium text-gray-500">Đơn Hàng Cao Cấp</h3>
          <p className="mt-2 text-3xl font-bold text-gray-900">{clusterStats.premium.length}</p>
          <p className="mt-1 text-xs text-gray-600">&gt; 1,000,000 VND</p>
          <p className="mt-2 text-sm font-semibold text-gray-700">
            Doanh thu: {clusterRevenue.premium.toLocaleString('vi-VN')} VND
          </p>
        </div>
      </div>

      {/* AI Analysis */}
      <div className="bg-gradient-to-r from-purple-50 to-blue-50 shadow rounded-lg p-6">
        <div className="flex items-center mb-4">
          <svg className="h-6 w-6 text-purple-600 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
          </svg>
          <h3 className="text-lg font-semibold text-gray-900">Phân Tích AI (Gemini)</h3>
        </div>
        <div className="bg-white rounded p-4 text-gray-700 whitespace-pre-wrap">
          {cleanAIText(data.analysis)}
        </div>
      </div>

      {/* Filter and Order Table */}
      <div className="bg-white shadow rounded-lg p-6">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-semibold text-gray-900">Chi Tiết Đơn Hàng</h3>
          <select
            value={selectedCluster}
            onChange={(e) => setSelectedCluster(e.target.value as ClusterType | 'all')}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm"
          >
            <option value="all">Tất Cả Đơn Hàng ({data.totalOrders})</option>
            <option value="lowValue">Giá Trị Thấp ({clusterStats.lowValue.length})</option>
            <option value="mediumValue">Giá Trị Trung Bình ({clusterStats.mediumValue.length})</option>
            <option value="highValue">Giá Trị Cao ({clusterStats.highValue.length})</option>
            <option value="premium">Cao Cấp ({clusterStats.premium.length})</option>
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Mã Đơn Hàng
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Khách Hàng
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Số Lượng
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Tổng Tiền (VND)
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Trạng Thái
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Phân Cụm
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredOrders.map((order) => (
                <tr key={order.orderId} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    {order.orderNumber}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {order.customerName}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {order.itemCount}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-gray-900">
                    {order.totalAmount.toLocaleString('vi-VN')}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${STATUS_COLORS[order.status] || 'bg-gray-100 text-gray-800'}`}>
                      {STATUS_LABELS[order.status] || order.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${CLUSTER_RULES[order.cluster].color}`}>
                      {CLUSTER_RULES[order.cluster].label}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Clustering Rules Reference */}
      <div className="bg-white shadow rounded-lg p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Quy Tắc Phân Cụm</h3>
        <div className="space-y-2 text-sm text-gray-600">
          <p><strong>Đơn Hàng Giá Trị Thấp:</strong> Đơn hàng có giá trị dưới 200,000 VND</p>
          <p><strong>Đơn Hàng Giá Trị Trung Bình:</strong> Đơn hàng có giá trị từ 200,000 - 500,000 VND</p>
          <p><strong>Đơn Hàng Giá Trị Cao:</strong> Đơn hàng có giá trị từ 500,000 - 1,000,000 VND</p>
          <p><strong>Đơn Hàng Cao Cấp:</strong> Đơn hàng có giá trị trên 1,000,000 VND</p>
        </div>
      </div>
    </div>
  );
}
