'use client';

/**
 * Edit Product Page
 * Form to edit existing product
 */

import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminProductsApi } from '@/lib/api/admin-products';
import { adminCategoriesApi } from '@/lib/api/admin-categories';
import { useState, useEffect } from 'react';
import ImageUpload from '@/components/admin/ImageUpload';

export default function EditProductPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const productId = parseInt(params.id as string);

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    price: '',
    compareAtPrice: '',
    stockQuantity: '',
    brand: '',
    material: '',
    gender: '',
    availableSizes: [] as string[],
    availableColors: [] as string[],
    active: true,
  });

  const [errors, setErrors] = useState<Record<string, string>>({});

  // States for multi-select inputs
  const [sizeInput, setSizeInput] = useState('');
  const [colorInput, setColorInput] = useState('');

  // Fetch product details
  const { data: product, isLoading } = useQuery({
    queryKey: ['admin-product', productId],
    queryFn: () => adminProductsApi.getById(productId),
  });

  // Fetch all categories
  const { data: categoriesData } = useQuery({
    queryKey: ['admin-categories-all'],
    queryFn: () => adminCategoriesApi.getAll(),
  });

  // State for selected category to add
  const [selectedCategoryId, setSelectedCategoryId] = useState<string>('');

  // Populate form when product loads
  useEffect(() => {
    if (product) {
      setFormData({
        name: product.name,
        description: product.description || '',
        price: product.price.toString(),
        compareAtPrice: product.compareAtPrice ? product.compareAtPrice.toString() : '',
        stockQuantity: product.stockQuantity.toString(),
        brand: product.brand || '',
        material: product.material || '',
        gender: product.gender || '',
        availableSizes: product.availableSizes || [],
        availableColors: product.availableColors || [],
        active: product.active,
      });
    }
  }, [product]);

  // Update product mutation
  const updateMutation = useMutation({
    mutationFn: (data: typeof formData) => {
      const productData = {
        ...data,
        price: parseFloat(data.price),
        compareAtPrice: data.compareAtPrice ? parseFloat(data.compareAtPrice) : undefined,
        stockQuantity: parseInt(data.stockQuantity) || 0,
      };
      return adminProductsApi.update(productId, productData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-product', productId] });
      queryClient.invalidateQueries({ queryKey: ['admin-products'] });
      alert('Cập nhật sản phẩm thành công!');
      router.push(`/admin/products/${productId}`);
    },
    onError: (error: any) => {
      alert('Lỗi: ' + (error.message || 'Không thể cập nhật sản phẩm'));
    },
  });

  // Add category mutation
  const addCategoryMutation = useMutation({
    mutationFn: (categoryId: number) =>
      adminProductsApi.addCategories(productId, [categoryId]),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin-product', productId] });
      setSelectedCategoryId('');
      alert('Thêm danh mục thành công!');
    },
    onError: (error: any) => {
      alert('Lỗi: ' + (error.message || 'Không thể thêm danh mục'));
    },
  });

  // Remove category mutation
  const removeCategoryMutation = useMutation({
    mutationFn: (categoryId: number) =>
      adminProductsApi.removeCategories(productId, [categoryId]),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin-product', productId] });
      alert('Xóa danh mục thành công!');
    },
    onError: (error: any) => {
      alert('Lỗi: ' + (error.message || 'Không thể xóa danh mục'));
    },
  });

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!formData.name || formData.name.trim() === '') {
      newErrors.name = 'Tên sản phẩm là bắt buộc';
    }

    if (!formData.price || parseFloat(formData.price) <= 0) {
      newErrors.price = 'Giá phải lớn hơn 0';
    }

    const stock = parseInt(formData.stockQuantity);
    if (formData.stockQuantity && (isNaN(stock) || stock < 0)) {
      newErrors.stockQuantity = 'Số lượng tồn kho phải là số không âm';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
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

  if (!product) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-red-600">Không tìm thấy sản phẩm</div>
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
            <h1 className="text-2xl font-bold text-gray-900">Chỉnh Sửa Sản Phẩm</h1>
            <p className="text-gray-600">ID: {product.id}</p>
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
                {/* Product Name */}
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Tên Sản Phẩm <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent ${
                      errors.name ? 'border-red-500' : 'border-gray-300'
                    }`}
                    placeholder="Nhập tên sản phẩm"
                  />
                  {errors.name && (
                    <p className="mt-1 text-sm text-red-600">{errors.name}</p>
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
                    rows={4}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="Nhập mô tả sản phẩm"
                  />
                </div>

                {/* Brand */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Thương Hiệu
                  </label>
                  <input
                    type="text"
                    value={formData.brand}
                    onChange={(e) => setFormData({ ...formData, brand: e.target.value })}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="Ví dụ: Nike, Adidas"
                  />
                </div>

                {/* Material */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Chất Liệu
                  </label>
                  <input
                    type="text"
                    value={formData.material}
                    onChange={(e) => setFormData({ ...formData, material: e.target.value })}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="Ví dụ: Cotton, Polyester"
                  />
                </div>

                {/* Gender */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Giới Tính
                  </label>
                  <select
                    value={formData.gender}
                    onChange={(e) => setFormData({ ...formData, gender: e.target.value })}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="">-- Chọn giới tính --</option>
                    <option value="Nam">Nam</option>
                    <option value="Nữ">Nữ</option>
                    <option value="Unisex">Unisex</option>
                  </select>
                </div>

                {/* Available Sizes */}
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Kích Cỡ Có Sẵn
                  </label>
                  <div className="flex gap-2 mb-2">
                    <input
                      type="text"
                      value={sizeInput}
                      onChange={(e) => setSizeInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          if (sizeInput.trim() && !formData.availableSizes.includes(sizeInput.trim())) {
                            setFormData({
                              ...formData,
                              availableSizes: [...formData.availableSizes, sizeInput.trim()]
                            });
                            setSizeInput('');
                          }
                        }
                      }}
                      className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      placeholder="Nhập size (S, M, L, XL, Freesize...)"
                    />
                    <button
                      type="button"
                      onClick={() => {
                        if (sizeInput.trim() && !formData.availableSizes.includes(sizeInput.trim())) {
                          setFormData({
                            ...formData,
                            availableSizes: [...formData.availableSizes, sizeInput.trim()]
                          });
                          setSizeInput('');
                        }
                      }}
                      className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                    >
                      Thêm
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {formData.availableSizes.map((size, index) => (
                      <span
                        key={index}
                        className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm flex items-center gap-2"
                      >
                        {size}
                        <button
                          type="button"
                          onClick={() => {
                            setFormData({
                              ...formData,
                              availableSizes: formData.availableSizes.filter((_, i) => i !== index)
                            });
                          }}
                          className="text-blue-600 hover:text-blue-800"
                        >
                          ×
                        </button>
                      </span>
                    ))}
                  </div>
                </div>

                {/* Available Colors */}
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Màu Sắc Có Sẵn
                  </label>
                  <div className="flex gap-2 mb-2">
                    <input
                      type="text"
                      value={colorInput}
                      onChange={(e) => setColorInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          if (colorInput.trim() && !formData.availableColors.includes(colorInput.trim())) {
                            setFormData({
                              ...formData,
                              availableColors: [...formData.availableColors, colorInput.trim()]
                            });
                            setColorInput('');
                          }
                        }
                      }}
                      className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      placeholder="Nhập màu (Đen, Trắng, Xanh...)"
                    />
                    <button
                      type="button"
                      onClick={() => {
                        if (colorInput.trim() && !formData.availableColors.includes(colorInput.trim())) {
                          setFormData({
                            ...formData,
                            availableColors: [...formData.availableColors, colorInput.trim()]
                          });
                          setColorInput('');
                        }
                      }}
                      className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                    >
                      Thêm
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {formData.availableColors.map((color, index) => (
                      <span
                        key={index}
                        className="px-3 py-1 bg-green-100 text-green-800 rounded-full text-sm flex items-center gap-2"
                      >
                        {color}
                        <button
                          type="button"
                          onClick={() => {
                            setFormData({
                              ...formData,
                              availableColors: formData.availableColors.filter((_, i) => i !== index)
                            });
                          }}
                          className="text-green-600 hover:text-green-800"
                        >
                          ×
                        </button>
                      </span>
                    ))}
                  </div>
                </div>
              </div>

            </div>

            {/* Product Images */}
            <div className="pt-6 border-t">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Hình Ảnh Sản Phẩm</h2>
              <ImageUpload
                productId={productId}
                images={product.images || []}
                availableColors={product.availableColors || []}
                categorySlug={product.slug}
                onUploadSuccess={() => {
                  queryClient.invalidateQueries({ queryKey: ['admin-product', productId] });
                }}
                onDeleteSuccess={() => {
                  queryClient.invalidateQueries({ queryKey: ['admin-product', productId] });
                }}
              />
            </div>

            {/* Categories Management */}
            <div className="pt-6 border-t">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Danh Mục Sản Phẩm</h2>

              {/* Current Categories */}
              <div className="mb-4">
                <p className="text-sm text-gray-600 mb-2">Danh mục hiện tại:</p>
                {product.categories && product.categories.length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {product.categories.map((category) => (
                      <span
                        key={category.id}
                        className="px-3 py-1 bg-purple-100 text-purple-800 rounded-full text-sm flex items-center gap-2"
                      >
                        {category.name}
                        <button
                          type="button"
                          onClick={() => {
                            if (window.confirm(`Xóa danh mục "${category.name}"?`)) {
                              removeCategoryMutation.mutate(category.id);
                            }
                          }}
                          disabled={removeCategoryMutation.isPending}
                          className="text-purple-600 hover:text-purple-800 disabled:opacity-50"
                        >
                          ×
                        </button>
                      </span>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-gray-500 italic">Chưa có danh mục nào</p>
                )}
              </div>

              {/* Add Category */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Thêm danh mục:
                </label>
                <div className="flex gap-2">
                  <select
                    value={selectedCategoryId}
                    onChange={(e) => setSelectedCategoryId(e.target.value)}
                    className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    disabled={!categoriesData || addCategoryMutation.isPending}
                  >
                    <option value="">-- Chọn danh mục --</option>
                    {categoriesData?.categories
                      .filter((cat) => !product.categories?.some((pc) => pc.id === cat.id))
                      .map((category) => (
                        <option key={category.id} value={category.id}>
                          {category.name}
                        </option>
                      ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => {
                      if (selectedCategoryId) {
                        addCategoryMutation.mutate(parseInt(selectedCategoryId));
                      }
                    }}
                    disabled={!selectedCategoryId || addCategoryMutation.isPending}
                    className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:bg-gray-400"
                  >
                    {addCategoryMutation.isPending ? 'Đang thêm...' : 'Thêm'}
                  </button>
                </div>
              </div>
            </div>

            {/* Pricing & Inventory */}
            <div className="pt-6 border-t">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Giá & Tồn Kho</h2>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {/* Price */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Giá Bán <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={formData.price}
                      onChange={(e) => setFormData({ ...formData, price: e.target.value })}
                      className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent ${
                        errors.price ? 'border-red-500' : 'border-gray-300'
                      }`}
                      placeholder="0.00"
                    />
                    <span className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-500">
                      ₫
                    </span>
                  </div>
                  {errors.price && (
                    <p className="mt-1 text-sm text-red-600">{errors.price}</p>
                  )}
                </div>

                {/* Compare At Price */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Giá Gốc
                  </label>
                  <div className="relative">
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={formData.compareAtPrice}
                      onChange={(e) => setFormData({ ...formData, compareAtPrice: e.target.value })}
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      placeholder="0.00"
                    />
                    <span className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-500">
                      ₫
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-gray-500">Giá trước khi giảm (tùy chọn)</p>
                </div>

                {/* Stock Quantity */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Số Lượng Tồn Kho
                  </label>
                  <input
                    type="number"
                    min="0"
                    value={formData.stockQuantity}
                    onChange={(e) => setFormData({ ...formData, stockQuantity: e.target.value })}
                    className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent ${
                      errors.stockQuantity ? 'border-red-500' : 'border-gray-300'
                    }`}
                    placeholder="0"
                  />
                  {errors.stockQuantity && (
                    <p className="mt-1 text-sm text-red-600">{errors.stockQuantity}</p>
                  )}
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
                  <div className="font-medium text-gray-900">Kích hoạt sản phẩm</div>
                  <div className="text-sm text-gray-600">Cho phép sản phẩm hiển thị trên trang web</div>
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
