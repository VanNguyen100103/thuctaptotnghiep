'use client';

/**
 * Edit Category Page
 * Form to edit existing category with image management
 */

import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminCategoriesApi } from '@/lib/api/admin-categories';
import { useState, useEffect } from 'react';

export default function EditCategoryPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const categoryId = parseInt(params.id as string);

  const [formData, setFormData] = useState({
    name: '',
    slug: '',
    description: '',
    displayOrder: 0,
    parentId: undefined as number | undefined,
    active: true,
  });

  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Fetch category details
  const { data: category, isLoading } = useQuery({
    queryKey: ['admin-category', categoryId],
    queryFn: () => adminCategoriesApi.getById(categoryId),
  });

  // Fetch all categories for parent selection
  const { data: categoriesData } = useQuery({
    queryKey: ['admin-categories-all'],
    queryFn: () => adminCategoriesApi.getAll(),
  });

  // Populate form when category loads
  useEffect(() => {
    if (category) {
      setFormData({
        name: category.name,
        slug: category.slug,
        description: category.description || '',
        displayOrder: category.displayOrder,
        parentId: category.parentId,
        active: category.active,
      });

      // Always sync imagePreview with category data
      setImagePreview(category.imageUrl || null);
    }
  }, [category]);

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: (data: typeof formData) => adminCategoriesApi.update(categoryId, data),
    onSuccess: async () => {
      // Invalidate queries and wait for them to refetch
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin-category', categoryId] }),
        queryClient.invalidateQueries({ queryKey: ['admin-categories-tree'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-categories-all'] }),
      ]);

      alert('Cập nhật danh mục thành công!');
      router.push('/admin/categories');
    },
    onError: (error: any) => {
      alert('Lỗi: ' + (error.response?.data?.error || error.message || 'Không thể cập nhật danh mục'));
    },
  });

  // Upload image mutation
  const uploadImageMutation = useMutation({
    mutationFn: (file: File) => adminCategoriesApi.uploadImage(categoryId, file),
    onSuccess: async (response) => {
      console.log('Upload response:', response);

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin-category', categoryId] }),
        queryClient.invalidateQueries({ queryKey: ['admin-categories-tree'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-categories-all'] }),
      ]);

      // Refetch category data to get updated image
      await queryClient.refetchQueries({ queryKey: ['admin-category', categoryId] });

      setImagePreview(response.imageUrl);
      setImageFile(null);
      alert('Upload ảnh thành công!');
    },
    onError: (error: any) => {
      console.error('Upload error:', error);
      alert('Lỗi upload ảnh: ' + (error.message || 'Không thể upload'));
    },
  });

  // Delete image mutation
  const deleteImageMutation = useMutation({
    mutationFn: () => adminCategoriesApi.deleteImage(categoryId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin-category', categoryId] }),
        queryClient.invalidateQueries({ queryKey: ['admin-categories-tree'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-categories-all'] }),
      ]);

      setImagePreview(null);
      alert('Xóa ảnh thành công!');
    },
    onError: (error: any) => {
      alert('Lỗi xóa ảnh: ' + (error.response?.data?.error || error.message));
    },
  });

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!formData.name || formData.name.trim() === '') {
      newErrors.name = 'Tên danh mục là bắt buộc';
    }

    if (!formData.slug || formData.slug.trim() === '') {
      newErrors.slug = 'Slug là bắt buộc';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (!file.type.startsWith('image/')) {
        alert('Vui lòng chọn file ảnh!');
        return;
      }

      if (file.size > 10 * 1024 * 1024) {
        alert('File ảnh phải nhỏ hơn 10MB!');
        return;
      }

      setImageFile(file);
    }
  };

  const handleUploadImage = () => {
    if (imageFile) {
      uploadImageMutation.mutate(imageFile);
    }
  };

  const handleDeleteImage = () => {
    if (window.confirm('Bạn có chắc muốn xóa ảnh này?')) {
      deleteImageMutation.mutate();
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    updateMutation.mutate(formData);
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-600">Đang tải...</div>
      </div>
    );
  }

  if (!category) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-red-600">Không tìm thấy danh mục</div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-4">
          <button
            onClick={() => router.back()}
            className="p-2 hover:bg-gray-100 rounded-lg"
          >
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
          </button>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Chỉnh Sửa Danh Mục</h1>
            <p className="text-gray-600">ID: {category.id}</p>
          </div>
        </div>
      </div>

      {/* Form */}
      <div className="bg-white rounded-lg shadow-md p-6">
        <form onSubmit={handleSubmit}>
          <div className="space-y-6">
            {/* Basic Information */}
            <div>
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Thông Tin Cơ Bản</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Name */}
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Tên Danh Mục <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent ${
                      errors.name ? 'border-red-500' : 'border-gray-300'
                    }`}
                    placeholder="Ví dụ: Áo Thun Nam"
                  />
                  {errors.name && (
                    <p className="mt-1 text-sm text-red-600">{errors.name}</p>
                  )}
                </div>

                {/* Slug */}
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Slug (URL thân thiện) <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formData.slug}
                    onChange={(e) => setFormData({ ...formData, slug: e.target.value })}
                    className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent ${
                      errors.slug ? 'border-red-500' : 'border-gray-300'
                    }`}
                    placeholder="ao-thun-nam"
                  />
                  {errors.slug && (
                    <p className="mt-1 text-sm text-red-600">{errors.slug}</p>
                  )}
                </div>

                {/* Description */}
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Mô Tả
                  </label>
                  <textarea
                    value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    rows={3}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="Mô tả ngắn gọn về danh mục này"
                  />
                </div>

                {/* Parent Category */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Danh Mục Cha (tùy chọn)
                  </label>
                  <select
                    value={formData.parentId || ''}
                    onChange={(e) => setFormData({ ...formData, parentId: e.target.value ? parseInt(e.target.value) : undefined })}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="">-- Danh mục gốc --</option>
                    {categoriesData?.categories
                      .filter((cat) => cat.id !== categoryId) // Exclude current category
                      .map((cat) => (
                        <option key={cat.id} value={cat.id}>
                          {cat.name}
                        </option>
                      ))}
                  </select>
                </div>

                {/* Display Order */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Thứ Tự Hiển Thị
                  </label>
                  <input
                    type="number"
                    value={formData.displayOrder}
                    onChange={(e) => setFormData({ ...formData, displayOrder: parseInt(e.target.value) || 0 })}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="0"
                  />
                </div>
              </div>
            </div>

            {/* Image Management */}
            <div className="pt-6 border-t">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Hình Ảnh Danh Mục</h2>

              <div className="space-y-4">
                {/* Current Image */}
                {imagePreview && (
                  <div>
                    <p className="text-sm text-gray-600 mb-2">Ảnh hiện tại:</p>
                    <div className="relative inline-block">
                      <img
                        src={imagePreview}
                        alt={category.name}
                        className="w-40 h-40 object-cover rounded-lg border-2 border-gray-200"
                        key={imagePreview}
                      />
                      <button
                        type="button"
                        onClick={handleDeleteImage}
                        disabled={deleteImageMutation.isPending}
                        className="absolute -top-2 -right-2 p-1 bg-red-500 text-white rounded-full hover:bg-red-600 disabled:opacity-50"
                      >
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                      </button>
                    </div>
                  </div>
                )}

                {/* New Image Upload */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    {imagePreview ? 'Thay đổi ảnh:' : 'Upload ảnh mới:'}
                  </label>
                  <div className="flex items-center gap-3">
                    <input
                      type="file"
                      accept="image/*"
                      onChange={handleImageChange}
                      className="flex-1 px-4 py-2 border border-gray-300 rounded-lg"
                    />
                    {imageFile && (
                      <button
                        type="button"
                        onClick={handleUploadImage}
                        disabled={uploadImageMutation.isPending}
                        className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-400"
                      >
                        {uploadImageMutation.isPending ? 'Đang upload...' : 'Upload'}
                      </button>
                    )}
                  </div>
                  <p className="mt-1 text-xs text-gray-500">
                    PNG, JPG, GIF, WEBP (tối đa 10MB)
                  </p>
                </div>
              </div>
            </div>

            {/* Status */}
            <div className="pt-6 border-t">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Trạng Thái</h2>
              <label className="flex items-center space-x-3 p-3 border border-gray-300 rounded-lg hover:bg-gray-50 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.active}
                  onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                  className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                />
                <div>
                  <div className="font-medium text-gray-900">Kích hoạt danh mục</div>
                  <div className="text-sm text-gray-600">Cho phép danh mục hiển thị trên trang web</div>
                </div>
              </label>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="mt-8 pt-6 border-t flex justify-end space-x-3">
            <button
              type="button"
              onClick={() => router.back()}
              className="px-6 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={updateMutation.isPending}
              className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400"
            >
              {updateMutation.isPending ? 'Đang cập nhật...' : 'Lưu thay đổi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
