'use client';

/**
 * Edit Coupon Page
 * Form to edit existing coupon
 */

import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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

interface UpdateCouponForm {
  description: string;
  discountValue: string;
  minimumOrderValue: string;
  maximumDiscountAmount: string;
  maxUsageCount: string;
  maxUsagePerUser: string;
  usedCount: string;
  startDate: string;
  expiryDate: string;
  active: boolean;
  notes: string;
}

export default function EditCouponPage() {
  const router = useRouter();
  const params = useParams();
  const queryClient = useQueryClient();
  const couponId = parseInt(params.id as string);

  const [formData, setFormData] = useState<UpdateCouponForm | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Fetch coupon data
  const { data: coupon, isLoading, error } = useQuery<Coupon>({
    queryKey: ['admin-coupon', couponId],
    queryFn: () => adminCouponsApi.getById(couponId),
    enabled: !isNaN(couponId),
  });

  // Initialize form data when coupon is loaded
  useEffect(() => {
    if (coupon) {
      setFormData({
        description: coupon.description,
        discountValue: coupon.discountValue.toString(),
        minimumOrderValue: coupon.minimumOrderValue?.toString() || '',
        maximumDiscountAmount: coupon.maximumDiscountAmount?.toString() || '',
        maxUsageCount: coupon.maxUsageCount?.toString() || '',
        maxUsagePerUser: coupon.maxUsagePerUser?.toString() || '',
        usedCount: coupon.usedCount.toString(),
        startDate: coupon.startDate ? new Date(coupon.startDate).toISOString().slice(0, 16) : '',
        expiryDate: coupon.expiryDate ? new Date(coupon.expiryDate).toISOString().slice(0, 16) : '',
        active: coupon.active,
        notes: coupon.notes || '',
      });
    }
  }, [coupon]);

  const updateMutation = useMutation({
    mutationFn: (data: any) => adminCouponsApi.update(couponId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
      queryClient.invalidateQueries({ queryKey: ['admin-coupon', couponId] });
      alert('Coupon đã được cập nhật thành công!');
      router.push('/admin/coupons');
    },
    onError: (error: any) => {
      const errorMessage = error.message || error.error || 'Không thể cập nhật coupon';
      alert('Lỗi: ' + errorMessage);
    },
  });

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value, type } = e.target;
    const checked = (e.target as HTMLInputElement).checked;

    setFormData(prev => {
      if (!prev) return prev;
      return {
        ...prev,
        [name]: type === 'checkbox' ? checked : value,
      };
    });

    if (errors[name]) {
      setErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  const validateForm = (): boolean => {
    if (!formData) return false;

    const newErrors: Record<string, string> = {};

    if (!formData.description.trim()) {
      newErrors.description = 'Mô tả là bắt buộc';
    }

    if (!formData.discountValue) {
      newErrors.discountValue = 'Giá trị giảm giá là bắt buộc';
    } else {
      const value = parseFloat(formData.discountValue);
      if (isNaN(value) || value <= 0) {
        newErrors.discountValue = 'Giá trị giảm giá phải lớn hơn 0';
      }
      if (coupon?.discountType === 'PERCENTAGE' && value > 100) {
        newErrors.discountValue = 'Phần trăm giảm giá không được vượt quá 100';
      }
    }

    if (formData.expiryDate && formData.startDate) {
      if (new Date(formData.expiryDate) <= new Date(formData.startDate)) {
        newErrors.expiryDate = 'Ngày hết hạn phải sau ngày bắt đầu';
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData || !validateForm()) {
      return;
    }

    const requestData: any = {
      description: formData.description.trim(),
      discountValue: parseFloat(formData.discountValue),
      active: formData.active,
    };

    // Optional fields
    if (formData.minimumOrderValue) {
      requestData.minimumOrderValue = parseFloat(formData.minimumOrderValue);
    }
    if (formData.maximumDiscountAmount) {
      requestData.maximumDiscountAmount = parseFloat(formData.maximumDiscountAmount);
    }
    if (formData.maxUsageCount) {
      requestData.maxUsageCount = parseInt(formData.maxUsageCount);
    }
    if (formData.maxUsagePerUser) {
      requestData.maxUsagePerUser = parseInt(formData.maxUsagePerUser);
    }
    if (formData.usedCount) {
      requestData.usedCount = parseInt(formData.usedCount);
    }
    if (formData.startDate) {
      requestData.startDate = new Date(formData.startDate).toISOString();
    }
    if (formData.expiryDate) {
      requestData.expiryDate = new Date(formData.expiryDate).toISOString();
    }
    if (formData.notes.trim()) {
      requestData.notes = formData.notes.trim();
    }

    updateMutation.mutate(requestData);
  };

  if (isLoading || !formData) {
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

  return (
    <div className="p-6 max-w-4xl mx-auto">
      {/* Header */}
      <div className="mb-6">
        <div className="flex items-center gap-2 text-sm text-gray-500 mb-2">
          <Link href="/admin/coupons" className="hover:text-gray-700">
            Quản Lý Coupon
          </Link>
          <span>/</span>
          <span className="text-gray-900">Chỉnh Sửa Coupon</span>
        </div>
        <h1 className="text-2xl font-bold text-gray-900">Chỉnh Sửa Coupon: {coupon.code}</h1>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow p-6 space-y-6">
        {/* Basic Info (Read-Only) */}
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900 border-b pb-2">Thông Tin Cơ Bản</h2>

          {/* Code (Read-Only) */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Mã Coupon
            </label>
            <input
              type="text"
              value={coupon.code}
              disabled
              className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-500 cursor-not-allowed"
            />
            <p className="mt-1 text-xs text-gray-500">Không thể thay đổi mã coupon sau khi tạo</p>
          </div>

          {/* Discount Type (Read-Only) */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Loại Giảm Giá
            </label>
            <input
              type="text"
              value={formatDiscountType(coupon.discountType)}
              disabled
              className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-500 cursor-not-allowed"
            />
            <p className="mt-1 text-xs text-gray-500">Không thể thay đổi loại giảm giá sau khi tạo</p>
          </div>

          {/* Description */}
          <div>
            <label htmlFor="description" className="block text-sm font-medium text-gray-700 mb-1">
              Mô Tả <span className="text-red-500">*</span>
            </label>
            <textarea
              id="description"
              name="description"
              value={formData.description}
              onChange={handleInputChange}
              rows={3}
              className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 ${
                errors.description ? 'border-red-500' : 'border-gray-300'
              }`}
            />
            {errors.description && <p className="mt-1 text-sm text-red-600">{errors.description}</p>}
          </div>
        </div>

        {/* Discount Settings */}
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900 border-b pb-2">Cài Đặt Giảm Giá</h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Discount Value */}
            <div>
              <label htmlFor="discountValue" className="block text-sm font-medium text-gray-700 mb-1">
                Giá Trị Giảm <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                id="discountValue"
                name="discountValue"
                value={formData.discountValue}
                onChange={handleInputChange}
                step="0.01"
                min="0"
                className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 ${
                  errors.discountValue ? 'border-red-500' : 'border-gray-300'
                }`}
                disabled={coupon.discountType === 'FREE_SHIPPING'}
              />
              {errors.discountValue && <p className="mt-1 text-sm text-red-600">{errors.discountValue}</p>}
            </div>

            {/* Active Status */}
            <div className="flex items-center h-full pt-6">
              <label className="flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  name="active"
                  checked={formData.active}
                  onChange={handleInputChange}
                  className="w-5 h-5 text-blue-600 rounded focus:ring-blue-500"
                />
                <span className="ml-2 text-sm font-medium text-gray-700">Coupon đang hoạt động</span>
              </label>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Minimum Order Value */}
            <div>
              <label htmlFor="minimumOrderValue" className="block text-sm font-medium text-gray-700 mb-1">
                Giá Trị Đơn Hàng Tối Thiểu
              </label>
              <input
                type="number"
                id="minimumOrderValue"
                name="minimumOrderValue"
                value={formData.minimumOrderValue}
                onChange={handleInputChange}
                step="1000"
                min="0"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Maximum Discount Amount */}
            <div>
              <label htmlFor="maximumDiscountAmount" className="block text-sm font-medium text-gray-700 mb-1">
                Giảm Tối Đa
              </label>
              <input
                type="number"
                id="maximumDiscountAmount"
                name="maximumDiscountAmount"
                value={formData.maximumDiscountAmount}
                onChange={handleInputChange}
                step="1000"
                min="0"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                disabled={coupon.discountType !== 'PERCENTAGE'}
              />
            </div>
          </div>
        </div>

        {/* Usage Limits */}
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900 border-b pb-2">Giới Hạn Sử Dụng</h2>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Max Usage Count */}
            <div>
              <label htmlFor="maxUsageCount" className="block text-sm font-medium text-gray-700 mb-1">
                Tổng Số Lượt Sử Dụng
              </label>
              <input
                type="number"
                id="maxUsageCount"
                name="maxUsageCount"
                value={formData.maxUsageCount}
                onChange={handleInputChange}
                min="1"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Max Usage Per User */}
            <div>
              <label htmlFor="maxUsagePerUser" className="block text-sm font-medium text-gray-700 mb-1">
                Số Lượt Dùng / Người
              </label>
              <input
                type="number"
                id="maxUsagePerUser"
                name="maxUsagePerUser"
                value={formData.maxUsagePerUser}
                onChange={handleInputChange}
                min="1"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Used Count (Admin can reset) */}
            <div>
              <label htmlFor="usedCount" className="block text-sm font-medium text-gray-700 mb-1">
                Đã Sử Dụng
              </label>
              <input
                type="number"
                id="usedCount"
                name="usedCount"
                value={formData.usedCount}
                onChange={handleInputChange}
                min="0"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
              />
              <p className="mt-1 text-xs text-gray-500">Admin có thể reset số lượt đã dùng</p>
            </div>
          </div>
        </div>

        {/* Validity Period */}
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900 border-b pb-2">Thời Gian Hiệu Lực</h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Start Date */}
            <div>
              <label htmlFor="startDate" className="block text-sm font-medium text-gray-700 mb-1">
                Ngày Bắt Đầu
              </label>
              <input
                type="datetime-local"
                id="startDate"
                name="startDate"
                value={formData.startDate}
                onChange={handleInputChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Expiry Date */}
            <div>
              <label htmlFor="expiryDate" className="block text-sm font-medium text-gray-700 mb-1">
                Ngày Hết Hạn
              </label>
              <input
                type="datetime-local"
                id="expiryDate"
                name="expiryDate"
                value={formData.expiryDate}
                onChange={handleInputChange}
                className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 ${
                  errors.expiryDate ? 'border-red-500' : 'border-gray-300'
                }`}
              />
              {errors.expiryDate && <p className="mt-1 text-sm text-red-600">{errors.expiryDate}</p>}
            </div>
          </div>
        </div>

        {/* Notes */}
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900 border-b pb-2">Ghi Chú</h2>

          <div>
            <label htmlFor="notes" className="block text-sm font-medium text-gray-700 mb-1">
              Ghi Chú Nội Bộ
            </label>
            <textarea
              id="notes"
              name="notes"
              value={formData.notes}
              onChange={handleInputChange}
              rows={3}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* Metadata (Read-Only) */}
        {(coupon.createdAt || coupon.updatedAt) && (
          <div className="space-y-2 pt-4 border-t text-sm text-gray-600">
            {coupon.createdAt && (
              <p>Tạo lúc: {new Date(coupon.createdAt).toLocaleString('vi-VN')}</p>
            )}
            {coupon.updatedAt && (
              <p>Cập nhật lúc: {new Date(coupon.updatedAt).toLocaleString('vi-VN')}</p>
            )}
          </div>
        )}

        {/* Actions */}
        <div className="flex items-center justify-end gap-3 pt-6 border-t">
          <Link
            href="/admin/coupons"
            className="px-6 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
          >
            Hủy
          </Link>
          <button
            type="submit"
            disabled={updateMutation.isPending}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:bg-gray-400 disabled:cursor-not-allowed flex items-center gap-2"
          >
            {updateMutation.isPending ? (
              <>
                <svg className="animate-spin h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Đang cập nhật...
              </>
            ) : (
              'Cập Nhật Coupon'
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
