'use client';

/**
 * Admin Coupon Management Page
 * List, search, filter, and manage coupons
 */

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminCouponsApi } from '@/lib/api/admin';
import Link from 'next/link';

type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT' | 'FREE_SHIPPING';

interface Coupon {
  id: number;
  code: string;
  description: string;
  discountType: DiscountType;
  discountValue: number;
  minimumOrderValue?: number;
  maximumDiscountAmount?: number;
  maxUsageCount?: number;
  maxUsagePerUser?: number;
  usedCount: number;
  startDate?: string;
  expiryDate?: string;
  active: boolean;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface CouponsResponse {
  coupons: Coupon[];
  currentPage: number;
  totalPages: number;
  totalCoupons: number;
}

export default function CouponsManagementPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  // Fetch coupons
  const { data, isLoading, error } = useQuery<CouponsResponse>({
    queryKey: ['admin-coupons', page],
    queryFn: () => adminCouponsApi.getAll({ page, size: 20 }),
    refetchOnMount: 'always',
    refetchOnWindowFocus: true,
  });

  // Delete mutation (soft delete - deactivate)
  const deleteMutation = useMutation({
    mutationFn: (couponId: number) => adminCouponsApi.delete(couponId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
      alert('Coupon đã được vô hiệu hóa thành công!');
    },
    onError: (error: any) => {
      alert('Lỗi: ' + (error.message || 'Không thể xóa coupon'));
    },
  });

  const handleDelete = (coupon: Coupon) => {
    if (confirm(`Bạn có chắc muốn vô hiệu hóa coupon "${coupon.code}"? Coupon sẽ không còn khả dụng.`)) {
      deleteMutation.mutate(coupon.id);
    }
  };

  const formatDiscountType = (type: DiscountType): string => {
    switch (type) {
      case 'PERCENTAGE':
        return 'Phần trăm';
      case 'FIXED_AMOUNT':
        return 'Số tiền cố định';
      case 'FREE_SHIPPING':
        return 'Miễn phí vận chuyển';
      default:
        return type;
    }
  };

  const formatDiscountValue = (coupon: Coupon): string => {
    if (coupon.discountType === 'PERCENTAGE') {
      return `${coupon.discountValue}%`;
    } else if (coupon.discountType === 'FIXED_AMOUNT') {
      return `${coupon.discountValue.toLocaleString('vi-VN')} ₫`;
    } else {
      return 'Miễn phí';
    }
  };

  const isExpired = (coupon: Coupon): boolean => {
    if (!coupon.expiryDate) return false;
    return new Date(coupon.expiryDate) < new Date();
  };

  const isUsageLimitReached = (coupon: Coupon): boolean => {
    if (!coupon.maxUsageCount) return false;
    return coupon.usedCount >= coupon.maxUsageCount;
  };

  if (error) {
    return (
      <div className="p-6">
        <div className="bg-red-50 border border-red-200 rounded-lg p-4">
          <p className="text-red-800">Lỗi: Không thể tải danh sách coupon</p>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Quản Lý Coupon</h1>
          <p className="text-sm text-gray-500 mt-1">
            Tạo và quản lý mã giảm giá cho khách hàng
          </p>
        </div>
        <Link
          href="/admin/coupons/new"
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Tạo Coupon
        </Link>
      </div>

      {/* Stats Cards */}
      {data && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="bg-white rounded-lg shadow p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-600">Tổng Coupon</p>
                <p className="text-2xl font-bold text-gray-900 mt-1">{data.totalCoupons}</p>
              </div>
              <div className="p-3 bg-blue-100 rounded-lg">
                <svg className="w-6 h-6 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
                </svg>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-lg shadow p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-600">Đang Hoạt Động</p>
                <p className="text-2xl font-bold text-green-600 mt-1">
                  {data.coupons.filter(c => c.active && !isExpired(c) && !isUsageLimitReached(c)).length}
                </p>
              </div>
              <div className="p-3 bg-green-100 rounded-lg">
                <svg className="w-6 h-6 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-lg shadow p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-600">Hết Hạn / Vô Hiệu</p>
                <p className="text-2xl font-bold text-red-600 mt-1">
                  {data.coupons.filter(c => !c.active || isExpired(c) || isUsageLimitReached(c)).length}
                </p>
              </div>
              <div className="p-3 bg-red-100 rounded-lg">
                <svg className="w-6 h-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Coupons Table */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center">
            <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
            <p className="mt-2 text-gray-600">Đang tải...</p>
          </div>
        ) : data && data.coupons.length > 0 ? (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Mã Code
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Mô Tả
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Loại
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Giá Trị
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Lượt Dùng
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Hạn
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Trạng Thái
                    </th>
                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Hành Động
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {data.coupons.map((coupon) => {
                    const expired = isExpired(coupon);
                    const limitReached = isUsageLimitReached(coupon);
                    const isValid = coupon.active && !expired && !limitReached;

                    return (
                      <tr key={coupon.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4 whitespace-nowrap">
                          <div className="text-sm font-medium text-gray-900">{coupon.code}</div>
                        </td>
                        <td className="px-6 py-4">
                          <div className="text-sm text-gray-900 max-w-xs truncate">
                            {coupon.description}
                          </div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className="text-sm text-gray-600">
                            {formatDiscountType(coupon.discountType)}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className="text-sm font-medium text-gray-900">
                            {formatDiscountValue(coupon)}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className="text-sm text-gray-600">
                            {coupon.usedCount} {coupon.maxUsageCount ? `/ ${coupon.maxUsageCount}` : ''}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          {coupon.expiryDate ? (
                            <div className="text-sm text-gray-600">
                              {new Date(coupon.expiryDate).toLocaleDateString('vi-VN')}
                            </div>
                          ) : (
                            <span className="text-sm text-gray-400">Không giới hạn</span>
                          )}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          {isValid ? (
                            <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800">
                              Hoạt động
                            </span>
                          ) : expired ? (
                            <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-yellow-100 text-yellow-800">
                              Hết hạn
                            </span>
                          ) : limitReached ? (
                            <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-orange-100 text-orange-800">
                              Hết lượt
                            </span>
                          ) : (
                            <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-red-100 text-red-800">
                              Vô hiệu
                            </span>
                          )}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                          <div className="flex items-center justify-end space-x-3">
                            {/* View Details */}
                            <Link
                              href={`/admin/coupons/${coupon.id}`}
                              className="text-gray-600 hover:text-gray-800"
                              title="Xem chi tiết"
                            >
                              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                              </svg>
                            </Link>

                            {/* Edit */}
                            <Link
                              href={`/admin/coupons/${coupon.id}/edit`}
                              className="text-blue-600 hover:text-blue-800"
                              title="Chỉnh sửa"
                            >
                              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                              </svg>
                            </Link>

                            {/* Delete */}
                            <button
                              onClick={() => handleDelete(coupon)}
                              className="text-red-600 hover:text-red-800"
                              title="Vô hiệu hóa"
                              disabled={deleteMutation.isPending}
                            >
                              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                              </svg>
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {data.totalPages > 1 && (
              <div className="bg-white px-4 py-3 flex items-center justify-between border-t border-gray-200 sm:px-6">
                <div className="flex-1 flex justify-between sm:hidden">
                  <button
                    onClick={() => setPage(Math.max(0, page - 1))}
                    disabled={page === 0}
                    className="relative inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50"
                  >
                    Trước
                  </button>
                  <button
                    onClick={() => setPage(page + 1)}
                    disabled={page >= data.totalPages - 1}
                    className="ml-3 relative inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50"
                  >
                    Sau
                  </button>
                </div>
                <div className="hidden sm:flex-1 sm:flex sm:items-center sm:justify-between">
                  <div>
                    <p className="text-sm text-gray-700">
                      Trang <span className="font-medium">{page + 1}</span> / <span className="font-medium">{data.totalPages}</span>
                      {' - '}
                      Tổng <span className="font-medium">{data.totalCoupons}</span> coupon
                    </p>
                  </div>
                  <div>
                    <nav className="relative z-0 inline-flex rounded-md shadow-sm -space-x-px" aria-label="Pagination">
                      <button
                        onClick={() => setPage(Math.max(0, page - 1))}
                        disabled={page === 0}
                        className="relative inline-flex items-center px-2 py-2 rounded-l-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 disabled:opacity-50"
                      >
                        Trước
                      </button>
                      <span className="relative inline-flex items-center px-4 py-2 border border-gray-300 bg-white text-sm font-medium text-gray-700">
                        Trang {page + 1}
                      </span>
                      <button
                        onClick={() => setPage(page + 1)}
                        disabled={page >= data.totalPages - 1}
                        className="relative inline-flex items-center px-2 py-2 rounded-r-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 disabled:opacity-50"
                      >
                        Sau
                      </button>
                    </nav>
                  </div>
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="p-8 text-center text-gray-500">
            <svg className="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
            </svg>
            <p className="mt-2">Chưa có coupon nào</p>
            <Link
              href="/admin/coupons/new"
              className="mt-4 inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              Tạo Coupon Đầu Tiên
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
