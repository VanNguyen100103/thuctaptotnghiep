'use client';

/**
 * Admin Dashboard Overview Page
 * Shows key metrics, charts, and recent activities
 */

import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '@/lib/api/admin';
import type { SalesResponse, SalesStats, OrderStatusStats } from '@/lib/api/admin';
import { useState } from 'react';
import {
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { cleanAIText } from '@/utils/textUtils';

type Period = '7days' | '30days' | '90days' | 'year';

const COLORS = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899'];

const STATUS_COLORS: Record<string, string> = {
  DELIVERED: '#10B981',
  PROCESSING: '#3B82F6',
  SHIPPED: '#F59E0B',
  PENDING: '#6B7280',
  CANCELLED: '#EF4444',
};

export default function AdminDashboard() {
  const [period, setPeriod] = useState<Period>('30days');

  // Fetch dashboard data
  const { data: overview, isLoading: overviewLoading } = useQuery({
    queryKey: ['dashboard-overview', period],
    queryFn: async () => {
      const data = await dashboardApi.getOverview(period);
      console.log('Overview data:', data);
      return data;
    },
  });

  const { data: salesData } = useQuery<SalesResponse>({
    queryKey: ['dashboard-sales', period],
    queryFn: async () => {
      const data = await dashboardApi.getSalesStats(period, 0, 30);
      console.log('Sales data:', data);

      // Transform salesByDate object to array for chart
      if (data && data.salesByDate) {
        const chartData = Object.entries(data.salesByDate)
          .map(([date, sales]) => ({
            date: date,
            sales: sales,
            revenue: sales,
            orders: sales > 0 ? 1 : 0,
          }))
          .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());

        return { ...data, chartData } as SalesResponse;
      }
      return data;
    },
  });

  const { data: topProducts } = useQuery({
    queryKey: ['dashboard-top-products'],
    queryFn: async () => {
      const data = await dashboardApi.getTopProducts(5);
      console.log('Top products:', data);
      return data;
    },
  });

  const { data: orderStatsResponse } = useQuery({
    queryKey: ['dashboard-order-stats'],
    queryFn: async () => {
      const data = await dashboardApi.getOrderStatusStats();
      console.log('Order stats:', data);
      return data;
    },
  });

  const orderStats = orderStatsResponse?.orderStatusStats;

  const { data: recentActivities } = useQuery({
    queryKey: ['dashboard-recent'],
    queryFn: async () => {
      const data = await dashboardApi.getRecentActivities(10);
      console.log('Recent activities:', data);
      return data;
    },
  });

  const { data: aiAnalysis } = useQuery({
    queryKey: ['dashboard-ai-analysis', period],
    queryFn: async () => {
      const data = await dashboardApi.getAIRevenueAnalysis(period);
      console.log('AI analysis:', data);
      return data;
    },
  });

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
    }).format(value);
  };

  const formatNumber = (value: number) => {
    return new Intl.NumberFormat('en-US').format(value);
  };

  const periodLabels = {
    '7days': '7 Ngày Qua',
    '30days': '30 Ngày Qua',
    '90days': '90 Ngày Qua',
    'year': 'Năm Nay',
  };

  if (overviewLoading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header with Period Selector */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Tổng Quan Dashboard</h1>
          <p className="text-sm text-gray-500 mt-1">Theo dõi hiệu suất cửa hàng của bạn</p>
        </div>
        <select
          value={period}
          onChange={(e) => setPeriod(e.target.value as Period)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          {Object.entries(periodLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {/* Total Revenue */}
        <div className="bg-white rounded-lg shadow p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-500">Tổng Doanh Thu</p>
              <p className="text-2xl font-bold text-gray-900 mt-2">
                {formatCurrency(overview?.revenue?.total || 0)}
              </p>
              <div className="flex items-center mt-2">
                <span className="text-xs text-gray-500">
                  Hôm nay: {formatCurrency(overview?.revenue?.today || 0)}
                </span>
              </div>
            </div>
            <div className="p-3 bg-green-100 rounded-lg">
              <svg className="w-8 h-8 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
          </div>
        </div>

        {/* Total Orders */}
        <div className="bg-white rounded-lg shadow p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-500">Tổng Đơn Hàng</p>
              <p className="text-2xl font-bold text-gray-900 mt-2">
                {formatNumber(overview?.orders?.total || 0)}
              </p>
              <div className="flex items-center mt-2 space-x-2">
                <span className="text-xs text-gray-500">
                  {overview?.orders?.shipped || 0} đã giao
                </span>
                <span className="text-xs text-gray-400">•</span>
                <span className="text-xs text-gray-500">
                  {overview?.orders?.pending || 0} chờ xử lý
                </span>
              </div>
            </div>
            <div className="p-3 bg-blue-100 rounded-lg">
              <svg className="w-8 h-8 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
              </svg>
            </div>
          </div>
        </div>

        {/* Total Products */}
        <div className="bg-white rounded-lg shadow p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-500">Tổng Sản Phẩm</p>
              <p className="text-2xl font-bold text-gray-900 mt-2">
                {formatNumber(overview?.products?.total || 0)}
              </p>
              <div className="flex items-center mt-2 space-x-2">
                <span className="text-xs text-green-600">
                  {overview?.products?.active || 0} đang bán
                </span>
                <span className="text-xs text-gray-400">•</span>
                <span className="text-xs text-red-600">
                  {overview?.products?.outOfStock || 0} hết hàng
                </span>
              </div>
            </div>
            <div className="p-3 bg-purple-100 rounded-lg">
              <svg className="w-8 h-8 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
              </svg>
            </div>
          </div>
        </div>

        {/* Total Users */}
        <div className="bg-white rounded-lg shadow p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-500">Tổng Người Dùng</p>
              <p className="text-2xl font-bold text-gray-900 mt-2">
                {formatNumber(overview?.users?.total || 0)}
              </p>
              <div className="flex items-center mt-2 space-x-2">
                <span className="text-xs text-green-600">
                  {overview?.users?.active || 0} hoạt động
                </span>
                <span className="text-xs text-gray-400">•</span>
                <span className="text-xs text-gray-500">
                  {overview?.users?.inactive || 0} không hoạt động
                </span>
              </div>
            </div>
            <div className="p-3 bg-orange-100 rounded-lg">
              <svg className="w-8 h-8 text-orange-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
            </div>
          </div>
        </div>
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Sales Trend Chart */}
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Xu Hướng Bán Hàng</h3>
          <div className="mb-2 text-xs text-gray-500">
            {salesData && `Tổng doanh số: ${salesData.totalSales?.toLocaleString()} đ • ${salesData.totalOrders} đơn hàng`}
          </div>
          {salesData?.chartData && salesData.chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={salesData.chartData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 12 }}
                  tickFormatter={(value) => {
                    const date = new Date(value);
                    return `${date.getMonth() + 1}/${date.getDate()}`;
                  }}
                />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip
                  formatter={(value: number) => `${value.toLocaleString()}đ`}
                  labelFormatter={(label) => `Ngày: ${label}`}
                />
                <Legend />
                <Line
                  type="monotone"
                  dataKey="sales"
                  stroke="#3B82F6"
                  strokeWidth={2}
                  name="Doanh số (đ)"
                  dot={{ r: 3 }}
                  activeDot={{ r: 5 }}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-[300px] text-gray-500">
              <div className="text-center">
                <p className="font-medium">Chưa có dữ liệu bán hàng</p>
                <p className="text-xs mt-1">Dữ liệu sẽ hiển thị khi có đơn hàng ĐÃ GIAO và ĐÃ THANH TOÁN</p>
              </div>
            </div>
          )}
        </div>

        {/* Order Status Distribution */}
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Phân Bố Trạng Thái Đơn Hàng</h3>
          <div className="mb-2 text-xs text-gray-500">
            {orderStatsResponse && `Tổng doanh thu: ${orderStatsResponse.totalConfirmedRevenue?.toLocaleString()} đ (Đã xác nhận)`}
          </div>
          {Array.isArray(orderStats) && orderStats.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={orderStats as any}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  label={(props: any) => {
                    const payload = props as OrderStatusStats;
                    const total = orderStats!.reduce((sum, item) => sum + item.count, 0);
                    const percent = ((payload.count / total) * 100).toFixed(0);
                    return `${payload.status} (${percent}%)`;
                  }}
                  outerRadius={100}
                  fill="#8884d8"
                  dataKey="count"
                  nameKey="status"
                >
                  {orderStats.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={STATUS_COLORS[entry.status] || COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(value: number) => [`${value} đơn hàng`]}
                />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-[300px] text-gray-500">
              <div className="text-center">
                <p className="font-medium">Chưa có dữ liệu đơn hàng</p>
                <p className="text-xs mt-1">Phân bố đơn hàng sẽ hiển thị khi có đơn hàng</p>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Top Products and AI Analysis */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Top Products */}
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Sản Phẩm Bán Chạy Nhất</h3>
          {topProducts && topProducts.length > 0 ? (
            <div className="space-y-4">
              {topProducts.map((product, index) => (
                <div key={product.productId} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                  <div className="flex items-center space-x-3">
                    <div className="flex-shrink-0 w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
                      <span className="text-sm font-bold text-blue-600">#{index + 1}</span>
                    </div>
                    <div>
                      <p className="font-medium text-gray-900">{product.productName}</p>
                      <p className="text-sm text-gray-500">{product.unitsSold} đã bán</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold text-gray-900">{formatCurrency(product.revenue)}</p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="flex items-center justify-center h-32 text-gray-500">
              Chưa có dữ liệu sản phẩm
            </div>
          )}
        </div>

        {/* AI Revenue Analysis */}
        <div className="bg-gradient-to-br from-blue-600 to-purple-700 rounded-lg shadow-lg p-6 text-white">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center">
              <div className="p-2 bg-white/20 rounded-lg mr-3">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                </svg>
              </div>
              <div>
                <h3 className="text-lg font-bold">Phân Tích Doanh Thu AI</h3>
                <p className="text-xs opacity-75">Hỗ trợ bởi Google Gemini</p>
              </div>
            </div>
          </div>

          {aiAnalysis ? (
            <div className="space-y-4">
              {/* Key Stats Grid */}
              <div className="grid grid-cols-2 gap-4">
                <div className="bg-white/15 backdrop-blur-sm rounded-lg p-4">
                  <p className="text-xs opacity-80 mb-1">Tổng Đơn Hàng</p>
                  <p className="text-2xl font-bold">{aiAnalysis.data.totalOrders}</p>
                </div>
                <div className="bg-white/15 backdrop-blur-sm rounded-lg p-4">
                  <p className="text-xs opacity-80 mb-1">Giá Trị ĐH TB</p>
                  <p className="text-xl font-bold">{(aiAnalysis.data.averageOrderValue / 1000).toFixed(0)}K đ</p>
                </div>
                <div className="bg-white/15 backdrop-blur-sm rounded-lg p-4">
                  <p className="text-xs opacity-80 mb-1">Ngày Đỉnh</p>
                  <p className="text-sm font-semibold">
                    {aiAnalysis.data.peakDay ? new Date(aiAnalysis.data.peakDay).toLocaleDateString() : 'N/A'}
                  </p>
                </div>
                <div className="bg-white/15 backdrop-blur-sm rounded-lg p-4">
                  <p className="text-xs opacity-80 mb-1">Tốc Độ Tăng Trưởng</p>
                  <p className="text-xl font-bold">{aiAnalysis.data.growthRate.toFixed(1)}%</p>
                </div>
              </div>

              

              {/* Full AI Analysis - Scrollable */}
              {aiAnalysis.aiAnalysis && (
                <div className="bg-white/10 backdrop-blur-sm rounded-lg p-4">
                  <div className="flex items-center mb-3">
                    <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                    <p className="text-sm font-semibold">Phân Tích Chi Tiết</p>
                  </div>
                  <div className="max-h-48 overflow-y-auto custom-scrollbar pr-2">
                    <p className="text-xs opacity-90 leading-relaxed whitespace-pre-wrap">
                      {cleanAIText(aiAnalysis.aiAnalysis)}
                    </p>
                  </div>
                </div>
              )}

              {/* Top Products Badges */}
              {aiAnalysis.data.top5Products && aiAnalysis.data.top5Products.length > 0 && (
                <div>
                  <p className="text-xs font-semibold mb-2 opacity-90">🏆 Sản Phẩm Hàng Đầu</p>
                  <div className="flex flex-wrap gap-2">
                    {aiAnalysis.data.top5Products.slice(0, 3).map((product, idx) => (
                      <span key={idx} className="text-xs px-3 py-1 bg-white/20 rounded-full">
                        {product.substring(0, 25)}{product.length > 25 ? '...' : ''}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              <div className="flex items-center justify-between pt-3 border-t border-white/20">
                <p className="text-xs opacity-60">
                  {new Date(aiAnalysis.generatedAt).toLocaleString()}
                </p>
                <span className="text-xs px-2 py-1 bg-white/20 rounded-full">
                  {aiAnalysis.data.periodDays} ngày
                </span>
              </div>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center h-64 space-y-3">
              <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-white"></div>
              <p className="text-sm opacity-75">Đang phân tích dữ liệu doanh thu...</p>
            </div>
          )}
        </div>
      </div>

      {/* Recent Activities */}
      <div className="bg-white rounded-lg shadow">
        <div className="p-6 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-900">Hoạt Động Gần Đây</h3>
        </div>
        <div className="divide-y divide-gray-200">
          {recentActivities && recentActivities.length > 0 ? (
            recentActivities.map((activity, index) => (
              <div key={index} className="p-4 hover:bg-gray-50 transition-colors">
                <div className="flex items-start justify-between">
                  <div className="flex items-start space-x-3">
                    <div className={`p-2 rounded-lg ${
                      activity.type === 'ORDER' ? 'bg-blue-100' :
                      activity.type === 'USER' ? 'bg-green-100' : 'bg-yellow-100'
                    }`}>
                      {activity.type === 'ORDER' ? (
                        <svg className="w-5 h-5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
                        </svg>
                      ) : activity.type === 'USER' ? (
                        <svg className="w-5 h-5 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                        </svg>
                      ) : (
                        <svg className="w-5 h-5 text-yellow-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                        </svg>
                      )}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-gray-900">{activity.description}</p>
                      {activity.email && (
                        <p className="text-xs text-gray-500 mt-1">{activity.email}</p>
                      )}
                      {activity.amount && (
                        <p className="text-xs text-green-600 mt-1 font-medium">{formatCurrency(activity.amount)}</p>
                      )}
                    </div>
                  </div>
                  <span className="text-xs text-gray-500">
                    {activity.createdAt ? new Date(activity.createdAt).toLocaleString() : 'Vừa xong'}
                  </span>
                </div>
              </div>
            ))
          ) : (
            <div className="p-8 text-center text-gray-500">
              Chưa có hoạt động gần đây
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
