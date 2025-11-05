'use client';

/**
 * Create New Coupon Page
 * Form to create new discount coupon
 */

import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminCouponsApi } from '@/lib/api/admin';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT' | 'FREE_SHIPPING';

interface CreateCouponForm {
  code: string;
  description: string;
  discountType: DiscountType;
  discountValue: string;
  minimumOrderValue: string;
  maximumDiscountAmount: string;
  maxUsageCount: string;
  maxUsagePerUser: string;
  startDate: string;
  expiryDate: string;
  notes: string;
}

export default function CreateCouponPage() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [formData, setFormData] = useState<CreateCouponForm>({
    code: '',
    description: '',
    discountType: 'PERCENTAGE',
    discountValue: '',
    minimumOrderValue: '',
    maximumDiscountAmount: '',
    maxUsageCount: '',
    maxUsagePerUser: '',
    startDate: '',
    expiryDate: '',
    notes: '',
  });

  const [errors, setErrors] = useState<Record<string, string>>({});

  const createMutation = useMutation({
    mutationFn: (data: any) => adminCouponsApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
      alert('Coupon đã được tạo thành công!');
      router.push('/admin/coupons');
    },
    onError: (error: any) => {
      const errorMessage = error.message || error.error || 'Không thể tạo coupon';
      alert('Lỗi: ' + errorMessage);
    },
  });

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value,
    }));

    // Clear error for this field
    if (errors[name]) {
      setErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!formData.code.trim()) {
      newErrors.code = 'Mã coupon là bắt buộc';
    }

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
      if (formData.discountType === 'PERCENTAGE' && value > 100) {
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

    if (!validateForm()) {
      return;
    }

    const requestData: any = {
      code: formData.code.toUpperCase().trim(),
      description: formData.description.trim(),
      discountType: formData.discountType,
      discountValue: parseFloat(formData.discountValue),
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
    if (formData.startDate) {
      requestData.startDate = new Date(formData.startDate).toISOString();
    }
    if (formData.expiryDate) {
      requestData.expiryDate = new Date(formData.expiryDate).toISOString();
    }
    if (formData.notes.trim()) {
      requestData.notes = formData.notes.trim();
    }

    createMutation.mutate(requestData);
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
          <span className="text-gray-900">Tạo Coupon Mới</span>
        </div>
        <h1 className="text-2xl font-bold text-gray-900">Tạo Coupon Mới</h1>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow p-6 space-y-6">
        {/* Basic Info */}
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900 border-b pb-2">Thông Tin Cơ Bản</h2>

          {/* Code */}
          <div>
            <label htmlFor="code" className="block text-sm font-medium text-gray-700 mb-1">
              Mã Coupon <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              id="code"
              name="code"
              value={formData.code}
              onChange={handleInputChange}
              placeholder="VD: SUMMER2025"
              className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 uppercase ${
                errors.code ? 'border-red-500' : 'border-gray-300'
              }`}
            />
            {errors.code && <p className="mt-1 text-sm text-red-600">{errors.code}</p>}
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
              placeholder="Mô tả về coupon này..."
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
            {/* Discount Type */}
            <div>
              <label htmlFor="discountType" className="block text-sm font-medium text-gray-700 mb-1">
                Loại Giảm Giá <span className="text-red-500">*</span>
              </label>
              <select
                id="discountType"
                name="discountType"
                value={formData.discountType}
                onChange={handleInputChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
              >
                <option value="PERCENTAGE">Phần trăm (%)</option>
                <option value="FIXED_AMOUNT">Số tiền cố định (₫)</option>
                <option value="FREE_SHIPPING">Miễn phí vận chuyển</option>
              </select>
            </div>

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
                placeholder={formData.discountType === 'PERCENTAGE' ? '10' : '50000'}
                className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 ${
                  errors.discountValue ? 'border-red-500' : 'border-gray-300'
                }`}
                disabled={formData.discountType === 'FREE_SHIPPING'}
              />
              {errors.discountValue && <p className="mt-1 text-sm text-red-600">{errors.discountValue}</p>}
              <p className="mt-1 text-xs text-gray-500">
                {formData.discountType === 'PERCENTAGE' && 'Nhập từ 0-100'}
                {formData.discountType === 'FIXED_AMOUNT' && 'Số tiền giảm cố định (VNĐ)'}
                {formData.discountType === 'FREE_SHIPPING' && 'Miễn phí vận chuyển (không cần giá trị)'}
              </p>
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
                placeholder="VD: 200000"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
              />
              <p className="mt-1 text-xs text-gray-500">Để trống nếu không giới hạn</p>
            </div>

            {/* Maximum Discount Amount (for PERCENTAGE) */}
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
                placeholder="VD: 100000"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                disabled={formData.discountType !== 'PERCENTAGE'}
              />
              <p className="mt-1 text-xs text-gray-500">
                {formData.discountType === 'PERCENTAGE' ? 'Số tiền giảm tối đa (VNĐ)' : 'Chỉ áp dụng cho loại phần trăm'}
              </p>
            </div>
          </div>
        </div>

        {/* Usage Limits */}
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900 border-b pb-2">Giới Hạn Sử Dụng</h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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
                placeholder="VD: 100"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
              />
              <p className="mt-1 text-xs text-gray-500">Để trống nếu không giới hạn</p>
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
                placeholder="VD: 1"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
              />
              <p className="mt-1 text-xs text-gray-500">Để trống nếu không giới hạn</p>
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
              <p className="mt-1 text-xs text-gray-500">Để trống để bắt đầu ngay</p>
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
              <p className="mt-1 text-xs text-gray-500">Để trống nếu không giới hạn</p>
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
              placeholder="Ghi chú cho admin (không hiển thị cho khách hàng)..."
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

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
            disabled={createMutation.isPending}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:bg-gray-400 disabled:cursor-not-allowed flex items-center gap-2"
          >
            {createMutation.isPending ? (
              <>
                <svg className="animate-spin h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Đang tạo...
              </>
            ) : (
              'Tạo Coupon'
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
