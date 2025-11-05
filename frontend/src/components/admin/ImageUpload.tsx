'use client';

import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { getToken, fetchCsrfToken } from '@/lib/api/client';

interface ProductImage {
  id: number;
  imageUrl: string;
  cloudinaryPublicId: string;
  altText?: string;
  isPrimary: boolean;
  displayOrder: number;
  thumbnailUrl?: string;
  color?: string; // Color variant
}

interface ImageUploadProps {
  productId: number;
  images: ProductImage[];
  availableColors?: string[]; // Colors from product
  categorySlug?: string; // Category slug for folder organization
  onUploadSuccess?: () => void;
  onDeleteSuccess?: () => void;
}

export default function ImageUpload({
  productId,
  images = [],
  availableColors = [],
  categorySlug: initialCategorySlug,
  onUploadSuccess,
  onDeleteSuccess
}: ImageUploadProps) {
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [previewUrls, setPreviewUrls] = useState<string[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const [selectedColor, setSelectedColor] = useState<string>('');
  const [categorySlug, setCategorySlug] = useState<string>(initialCategorySlug || '');
  const queryClient = useQueryClient();

  // Debug: Log available colors
  console.log('ImageUpload - availableColors:', availableColors);
  console.log('ImageUpload - availableColors.length:', availableColors?.length);

  // Upload images mutation
  const uploadMutation = useMutation({
    mutationFn: async (files: File[]) => {
      const formData = new FormData();
      files.forEach((file) => {
        formData.append('images', file);
      });

      const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || '';
      const token = getToken();
      const csrfToken = await fetchCsrfToken();

      const headers: Record<string, string> = {};
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }
      if (csrfToken) {
        headers['X-XSRF-TOKEN'] = csrfToken;
      }

      // Build query params
      const params = new URLSearchParams();
      if (categorySlug) params.append('categorySlug', categorySlug);
      if (selectedColor) params.append('color', selectedColor);
      const queryString = params.toString();

      const response = await fetch(
        `${API_BASE_URL}/api/products/${productId}/images${queryString ? `?${queryString}` : ''}`,
        {
          method: 'POST',
          headers,
          body: formData,
          credentials: 'include',
        }
      );

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.error || 'Upload failed');
      }

      return await response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-product', productId] });
      setSelectedFiles([]);
      setPreviewUrls([]);
      alert('Upload hình ảnh thành công!');
      onUploadSuccess?.();
    },
    onError: (error: any) => {
      alert('Lỗi upload: ' + (error.message || 'Không thể upload hình ảnh'));
    },
  });

  // Delete image mutation
  const deleteMutation = useMutation({
    mutationFn: async (imageId: number) => {
      const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost';
      const token = getToken();
      const csrfToken = await fetchCsrfToken();

      const headers: Record<string, string> = {};
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }
      if (csrfToken) {
        headers['X-XSRF-TOKEN'] = csrfToken;
      }

      const response = await fetch(
        `${API_BASE_URL}/api/products/images/${imageId}`,
        {
          method: 'DELETE',
          headers,
          credentials: 'include',
        }
      );

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.error || 'Delete failed');
      }

      return await response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-product', productId] });
      alert('Xóa hình ảnh thành công!');
      onDeleteSuccess?.();
    },
    onError: (error: any) => {
      alert('Lỗi xóa ảnh: ' + (error.message || 'Không thể xóa hình ảnh'));
    },
  });

  const handleFileSelect = (files: FileList | null) => {
    if (!files || files.length === 0) return;

    const fileArray = Array.from(files);

    // Validate file types
    const validFiles = fileArray.filter((file) => {
      if (!file.type.startsWith('image/')) {
        alert(`${file.name} không phải là file ảnh`);
        return false;
      }
      // Max 10MB per file
      if (file.size > 10 * 1024 * 1024) {
        alert(`${file.name} vượt quá 10MB`);
        return false;
      }
      return true;
    });

    setSelectedFiles((prev) => [...prev, ...validFiles]);

    // Create preview URLs
    validFiles.forEach((file) => {
      const reader = new FileReader();
      reader.onloadend = () => {
        setPreviewUrls((prev) => [...prev, reader.result as string]);
      };
      reader.readAsDataURL(file);
    });
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    handleFileSelect(e.dataTransfer.files);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const removePreview = (index: number) => {
    setSelectedFiles((prev) => prev.filter((_, i) => i !== index));
    setPreviewUrls((prev) => prev.filter((_, i) => i !== index));
  };

  const handleUpload = () => {
    if (selectedFiles.length === 0) {
      alert('Vui lòng chọn ít nhất một hình ảnh');
      return;
    }
    uploadMutation.mutate(selectedFiles);
  };

  const handleDelete = (imageId: number) => {
    if (confirm('Bạn có chắc muốn xóa hình ảnh này?')) {
      deleteMutation.mutate(imageId);
    }
  };

  return (
    <div className="space-y-6">
      {/* Current Images */}
      {images && images.length > 0 && (
        <div>
          <h3 className="text-lg font-semibold text-gray-900 mb-3">
            Hình Ảnh Hiện Tại ({images.length})
          </h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {images.map((image) => (
              <div key={image.id} className="relative group">
                <img
                  src={image.imageUrl}
                  alt={image.altText || 'Product image'}
                  className="w-full h-48 object-cover rounded-lg border-2 border-gray-200"
                />
                <div className="absolute top-2 left-2 flex flex-col gap-1">
                  {image.isPrimary && (
                    <span className="bg-blue-600 text-white text-xs px-2 py-1 rounded">
                      Ảnh chính
                    </span>
                  )}
                  {image.color && (
                    <span className="bg-purple-600 text-white text-xs px-2 py-1 rounded">
                      {image.color}
                    </span>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => handleDelete(image.id)}
                  disabled={deleteMutation.isPending}
                  className="absolute top-2 right-2 bg-red-600 text-white p-2 rounded-full opacity-0 group-hover:opacity-100 transition-opacity hover:bg-red-700 disabled:bg-gray-400"
                >
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Upload New Images */}
      <div>
        <h3 className="text-lg font-semibold text-gray-900 mb-3">
          Upload Hình Ảnh Mới
        </h3>

        {/* Color & Category Selection */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
          {/* Color Selection */}
          {availableColors.length > 0 && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Màu sắc (tùy chọn)
              </label>
              <select
                value={selectedColor}
                onChange={(e) => setSelectedColor(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">-- Tất cả màu --</option>
                {availableColors.map((color) => (
                  <option key={color} value={color}>
                    {color}
                  </option>
                ))}
              </select>
              <p className="mt-1 text-xs text-gray-500">
                Chọn màu để ảnh hiện khi khách hàng chọn màu này
              </p>
            </div>
          )}

          {/* Category Slug */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Category Slug (tùy chọn)
            </label>
            <input
              type="text"
              value={categorySlug}
              onChange={(e) => setCategorySlug(e.target.value)}
              placeholder="e.g., ao-thun-nam"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p className="mt-1 text-xs text-gray-500">
              Dùng để tổ chức thư mục trên Cloudinary
            </p>
          </div>
        </div>

        {/* Drag & Drop Zone */}
        <div
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          className={`border-2 border-dashed rounded-lg p-8 text-center transition-colors ${
            isDragging
              ? 'border-blue-500 bg-blue-50'
              : 'border-gray-300 hover:border-gray-400'
          }`}
        >
          <svg
            className="mx-auto h-12 w-12 text-gray-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
            />
          </svg>
          <p className="mt-2 text-sm text-gray-600">
            Kéo thả hình ảnh vào đây hoặc
          </p>
          <label className="mt-2 inline-block">
            <span className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 cursor-pointer">
              Chọn file
            </span>
            <input
              type="file"
              multiple
              accept="image/*"
              onChange={(e) => handleFileSelect(e.target.files)}
              className="hidden"
            />
          </label>
          <p className="mt-2 text-xs text-gray-500">
            PNG, JPG, GIF tối đa 10MB mỗi file
          </p>
        </div>

        {/* Preview Selected Files */}
        {previewUrls.length > 0 && (
          <div className="mt-4">
            <h4 className="font-medium text-gray-900 mb-2">
              File đã chọn ({selectedFiles.length})
            </h4>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {previewUrls.map((url, index) => (
                <div key={index} className="relative group">
                  <img
                    src={url}
                    alt={`Preview ${index + 1}`}
                    className="w-full h-48 object-cover rounded-lg border-2 border-gray-200"
                  />
                  <div className="absolute bottom-2 left-2 right-2 bg-black bg-opacity-50 text-white text-xs p-1 rounded truncate">
                    {selectedFiles[index].name}
                  </div>
                  <button
                    type="button"
                    onClick={() => removePreview(index)}
                    className="absolute top-2 right-2 bg-red-600 text-white p-1 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>

            {/* Upload Button */}
            <div className="mt-4 flex justify-end">
              <button
                type="button"
                onClick={handleUpload}
                disabled={uploadMutation.isPending || selectedFiles.length === 0}
                className="px-6 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-400"
              >
                {uploadMutation.isPending
                  ? 'Đang upload...'
                  : `Upload ${selectedFiles.length} hình ảnh`}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
