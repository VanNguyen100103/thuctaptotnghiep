'use client';

/**
 * View Coupon Detail Page
 * Display full coupon information and usage statistics
 */

import { useQuery } from '@tanstack/react-query';
import { adminCouponsApi } from '@/lib/api/admin';
import { useRouter, useParams } from 'next/navigation';
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

export default function CouponDetailPage() {
  const router = useRouter();
  const params = useParams();
  const couponId = parseInt(params.id as string);

  // Fetch coupon data
  const { data: coupon, isLoading, error } = useQuery<Coupon>({
    queryKey: ['admin-coupon', couponId],
    queryFn: () => adminCouponsApi.getById(couponId),
    enabled: !isNaN(couponId),
  });

  if (isLoading) {
    return (
      <div className="p-6">
        <div className="flex items-center justify-center h-64">
          <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
          <p className="ml-3 text-gray-600">Đang tải...</p>
        </div>
      </div>
    );
  }

  if (error || !coupon) {
    return (
      <div className="p-6">
        <div className="bg-red-50 border border-red-200 rounded-lg p-4">
          <p className="text-red-800">Lỗi: Không thể tải thông tin coupon</p>
          <Link href="/admin/coupons" className="text-blue-600 hover:underline mt-2 inline-block">
            Quay lại danh sách coupon
          </Link>
        </div>
      </div>
    );
  }

  const formatDiscountType = (type: DiscountType): string => {
    switch (type) {
      case 'PERCENTAGE':
        return 'Phần trăm (%)';
      case 'FIXED_AMOUNT':
        return 'Số tiền cố định (₫)';
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
      return 'Miễn phí vận chuyển';
    }
  };

  const isExpired = (): boolean => {
    if (!coupon.expiryDate) return false;
    return new Date(coupon.expiryDate) < new Date();
  };

  const isUsageLimitReached = (): boolean => {
    if (!coupon.maxUsageCount) return false;
    return coupon.usedCount >= coupon.maxUsageCount;
  };

  const expired = isExpired();
  const limitReached = isUsageLimitReached();
  const isValid = coupon.active && !expired && !limitReached;

  const usagePercentage = coupon.maxUsageCount
    ? (coupon.usedCount / coupon.maxUsageCount) * 100
    : 0;

  return (
    <div className="p-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="mb-6">
        <div className="flex items-center gap-2 text-sm text-gray-500 mb-2">
          <Link href="/admin/coupons" className="hover:text-gray-700">
            Quản Lý Coupon
          </Link>
          <span>/</span>
          <span className="text-gray-900">Chi Tiết Coupon</span>
        </div>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <h1 className="text-2xl font-bold text-gray-900">{coupon.code}</h1>
            {isValid ? (
              <span className="px-3 py-1 text-sm font-semibold rounded-full bg-green-100 text-green-800">
                Hoạt động
              </span>
            ) : expired ? (
              <span className="px-3 py-1 text-sm font-semibold rounded-full bg-yellow-100 text-yellow-800">
                Hết hạn
              </span>
            ) : limitReached ? (
              <span className="px-3 py-1 text-sm font-semibold rounded-full bg-orange-100 text-orange-800">
                Hết lượt
              </span>
            ) : (
              <span className="px-3 py-1 text-sm font-semibold rounded-full bg-red-100 text-red-800">
                Vô hiệu
              </span>
            )}
          </div>
          <Link
            href={`/admin/coupons/${coupon.id}/edit`}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
            Chỉnh Sửa
          </Link>
        </div>
      </div>

      {/* Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Info */}
        <div className="lg:col-span-2 space-y-6">
          {/* Basic Info */}
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4 pb-2 border-b">Thông Tin Cơ Bản</h2>
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium text-gray-500">Mã Coupon</label>
                <p className="text-lg font-bold text-gray-900 mt-1">{coupon.code}</p>
              </div>
              <div>
                <label className="text-sm font-medium text-gray-500">Mô Tả</label>
                <p className="text-gray-900 mt-1">{coupon.description}</p>
              </div>
            </div>
          </div>

          {/* Discount Settings */}
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4 pb-2 border-b">Cài Đặt Giảm Giá</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium text-gray-500">Loại Giảm Giá</label>
                <p className="text-gray-900 mt-1">{formatDiscountType(coupon.discountType)}</p>
              </div>
              <div>
                <label className="text-sm font-medium text-gray-500">Giá Trị Giảm</label>
                <p className="text-lg font-bold text-blue-600 mt-1">{formatDiscountValue(coupon)}</p>
              </div>
              {coupon.minimumOrderValue && (
                <div>
                  <label className="text-sm font-medium text-gray-500">Đơn Hàng Tối Thiểu</label>
                  <p className="text-gray-900 mt-1">{coupon.minimumOrderValue.toLocaleString('vi-VN')} ₫</p>
                </div>
              )}
              {coupon.maximumDiscountAmount && (
                <div>
                  <label className="text-sm font-medium text-gray-500">Giảm Tối Đa</label>
                  <p className="text-gray-900 mt-1">{coupon.maximumDiscountAmount.toLocaleString('vi-VN')} ₫</p>
                </div>
              )}
            </div>
          </div>

          {/* Validity Period */}
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4 pb-2 border-b">Thời Gian Hiệu Lực</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium text-gray-500">Ngày Bắt Đầu</label>
                <p className="text-gray-900 mt-1">
                  {coupon.startDate
                    ? new Date(coupon.startDate).toLocaleString('vi-VN')
                    : <span className="text-gray-400">Ngay lập tức</span>
                  }
                </p>
              </div>
              <div>
                <label className="text-sm font-medium text-gray-500">Ngày Hết Hạn</label>
                <p className={`mt-1 ${expired ? 'text-red-600 font-semibold' : 'text-gray-900'}`}>
                  {coupon.expiryDate
                    ? new Date(coupon.expiryDate).toLocaleString('vi-VN')
                    : <span className="text-gray-400">Không giới hạn</span>
                  }
                  {expired && <span className="ml-2">(Đã hết hạn)</span>}
                </p>
              </div>
            </div>
          </div>

          {/* Notes */}
          {coupon.notes && (
            <div className="bg-white rounded-lg shadow p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4 pb-2 border-b">Ghi Chú Nội Bộ</h2>
              <p className="text-gray-700 whitespace-pre-wrap">{coupon.notes}</p>
            </div>
          )}
        </div>

        {/* Sidebar Stats */}
        <div className="space-y-6">
          {/* Usage Stats */}
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4 pb-2 border-b">Thống Kê Sử Dụng</h2>
            <div className="space-y-4">
              {/* Usage Count */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-sm font-medium text-gray-500">Đã Sử Dụng</label>
                  <span className="text-lg font-bold text-gray-900">
                    {coupon.usedCount} {coupon.maxUsageCount ? `/ ${coupon.maxUsageCount}` : ''}
                  </span>
                </div>
                {coupon.maxUsageCount && (
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className={`h-2 rounded-full transition-all ${
                        usagePercentage >= 100 ? 'bg-red-500' :
                        usagePercentage >= 75 ? 'bg-orange-500' :
                        'bg-green-500'
                      }`}
                      style={{ width: `${Math.min(usagePercentage, 100)}%` }}
                    ></div>
                  </div>
                )}
                {!coupon.maxUsageCount && (
                  <p className="text-xs text-gray-500">Không giới hạn</p>
                )}
              </div>

              {/* Per User Limit */}
              {coupon.maxUsagePerUser && (
                <div className="pt-4 border-t">
                  <label className="text-sm font-medium text-gray-500">Giới Hạn / Người</label>
                  <p className="text-lg font-bold text-gray-900 mt-1">{coupon.maxUsagePerUser} lượt</p>
                </div>
              )}

              {/* Remaining */}
              {coupon.maxUsageCount && (
                <div className="pt-4 border-t">
                  <label className="text-sm font-medium text-gray-500">Còn Lại</label>
                  <p className={`text-lg font-bold mt-1 ${
                    coupon.maxUsageCount - coupon.usedCount <= 0 ? 'text-red-600' : 'text-green-600'
                  }`}>
                    {Math.max(0, coupon.maxUsageCount - coupon.usedCount)} lượt
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Status Card */}
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4 pb-2 border-b">Trạng Thái</h2>
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-600">Kích hoạt</span>
                <span className={`font-semibold ${coupon.active ? 'text-green-600' : 'text-red-600'}`}>
                  {coupon.active ? 'Có' : 'Không'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-600">Hết hạn</span>
                <span className={`font-semibold ${expired ? 'text-red-600' : 'text-green-600'}`}>
                  {expired ? 'Có' : 'Không'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-600">Hết lượt</span>
                <span className={`font-semibold ${limitReached ? 'text-red-600' : 'text-green-600'}`}>
                  {limitReached ? 'Có' : 'Không'}
                </span>
              </div>
              <div className="flex items-center justify-between pt-3 border-t">
                <span className="text-sm text-gray-600">Có thể dùng</span>
                <span className={`font-bold ${isValid ? 'text-green-600' : 'text-red-600'}`}>
                  {isValid ? 'Có' : 'Không'}
                </span>
              </div>
            </div>
          </div>

          {/* Metadata */}
          <div className="bg-gray-50 rounded-lg p-6">
            <h2 className="text-sm font-semibold text-gray-700 mb-3">Metadata</h2>
            <div className="space-y-2 text-xs text-gray-600">
              <div>
                <span className="font-medium">ID:</span> #{coupon.id}
              </div>
              {coupon.createdAt && (
                <div>
                  <span className="font-medium">Tạo lúc:</span><br />
                  {new Date(coupon.createdAt).toLocaleString('vi-VN')}
                </div>
              )}
              {coupon.updatedAt && (
                <div>
                  <span className="font-medium">Cập nhật lúc:</span><br />
                  {new Date(coupon.updatedAt).toLocaleString('vi-VN')}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
