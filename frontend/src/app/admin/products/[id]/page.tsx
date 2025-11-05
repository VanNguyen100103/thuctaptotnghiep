'use client';

/**
 * Product Detail Page
 * View and manage product details
 */

import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminProductsApi, type Product } from '@/lib/api/admin-products';
import { useState } from 'react';
import Link from 'next/link';

export default function ProductDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const productId = parseInt(params.id as string);

  const [stockInput, setStockInput] = useState('');
  const [showStockModal, setShowStockModal] = useState(false);

  // Fetch product details
  const { data: product, isLoading } = useQuery({
    queryKey: ['admin-product', productId],
    
    queryFn: () => adminProductsApi.getById(productId),
    
  });

  console.log("Product Details:", product);

  // Toggle status mutation
  const toggleStatusMutation = useMutation({
    mutationFn: (active: boolean) => adminProductsApi.updateStatus(productId, active),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-product', productId] });
      queryClient.invalidateQueries({ queryKey: ['admin-products'] });
    },
  });

  // Update stock mutation
  const updateStockMutation = useMutation({
    mutationFn: (stockQuantity: number) => adminProductsApi.updateStock(productId, stockQuantity),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-product', productId] });
      queryClient.invalidateQueries({ queryKey: ['admin-products'] });
      setShowStockModal(false);
      setStockInput('');
      alert('Cập nhật tồn kho thành công!');
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: () => adminProductsApi.delete(productId),
    onSuccess: () => {
      alert('Xóa sản phẩm thành công!');
      router.push('/admin/products');
    },
  });

  const handleToggleStatus = () => {
    if (!product) return;
    if (confirm(`Bạn có chắc muốn ${product.active ? 'vô hiệu hóa' : 'kích hoạt'} sản phẩm này?`)) {
      toggleStatusMutation.mutate(!product.active);
    }
  };

  const handleUpdateStock = () => {
    const quantity = parseInt(stockInput);
    if (isNaN(quantity) || quantity < 0) {
      alert('Vui lòng nhập số lượng hợp lệ');
      return;
    }
    updateStockMutation.mutate(quantity);
  };

  const handleDelete = () => {
    if (!product) return;
    if (confirm(`Bạn có chắc muốn xóa sản phẩm "${product.name}"? Sản phẩm sẽ bị vô hiệu hóa.`)) {
      deleteMutation.mutate();
    }
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
            <h1 className="text-2xl font-bold text-gray-900">Chi Tiết Sản Phẩm</h1>
            <p className="text-gray-600">ID: {product.id}</p>
          </div>
        </div>

        <div className="flex space-x-3">
          <Link
            href={`/admin/products/${product.id}/edit`}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            Chỉnh Sửa
          </Link>
          <button
            onClick={handleToggleStatus}
            className={`px-4 py-2 rounded-lg ${
              product.active
                ? 'bg-orange-100 text-orange-700 hover:bg-orange-200'
                : 'bg-green-100 text-green-700 hover:bg-green-200'
            }`}
          >
            {product.active ? 'Vô Hiệu Hóa' : 'Kích Hoạt'}
          </button>
          <button
            onClick={handleDelete}
            className="px-4 py-2 bg-red-100 text-red-700 rounded-lg hover:bg-red-200"
          >
            Xóa
          </button>
        </div>
      </div>

      {/* Product Info Card */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Info */}
        <div className="lg:col-span-2 bg-white rounded-lg shadow-md p-6 space-y-6">
          {/* Image */}
          <div>
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Hình Ảnh</h2>
            {product.images && product.images.length > 0 ? (
              <img
                src={product.images.find((img) => img.isPrimary)?.imageUrl || product.images[0]?.imageUrl}
                alt={product.name}
                className="w-full h-64 object-cover rounded-lg"
                onError={(e) => {
                  e.currentTarget.style.display = 'none';
                  e.currentTarget.nextElementSibling?.classList.remove('hidden');
                }}
              />
            ) : null}
            {(!product.images || product.images.length === 0) && (
              <div className="w-full h-64 bg-gray-200 rounded-lg flex items-center justify-center">
                <svg className="w-24 h-24 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
              </div>
            )}
          </div>

          {/* Basic Info */}
          <div>
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Thông Tin Cơ Bản</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Tên Sản Phẩm</label>
                <p className="text-gray-900">{product.name}</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Mô Tả</label>
                <p className="text-gray-900 whitespace-pre-wrap">{product.description || 'Chưa có mô tả'}</p>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Thương Hiệu</label>
                  <p className="text-gray-900">{product.brand || '—'}</p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Chất Liệu</label>
                  <p className="text-gray-900">{product.material || '—'}</p>
                </div>
              </div>
            </div>
          </div>

          {/* Categories */}
          {product.categories && product.categories.length > 0 && (
            <div>
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Danh Mục</h2>
              <div className="flex flex-wrap gap-2">
                {product.categories.map((category) => (
                  <span
                    key={category.id}
                    className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm font-medium"
                  >
                    {category.name}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          {/* Status Card */}
          <div className="bg-white rounded-lg shadow-md p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Trạng Thái</h2>
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-600">Trạng thái bán</span>
                <span
                  className={`px-3 py-1 rounded-full text-sm font-semibold ${
                    product.active
                      ? 'bg-green-100 text-green-800'
                      : 'bg-red-100 text-red-800'
                  }`}
                >
                  {product.active ? 'Đang bán' : 'Ngừng bán'}
                </span>
              </div>
            </div>
          </div>

          {/* Price & Stock Card */}
          <div className="bg-white rounded-lg shadow-md p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Giá & Tồn Kho</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Giá Bán</label>
                <p className="text-2xl font-bold text-blue-600">
                  {product.price.toLocaleString('vi-VN')} ₫
                </p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Tồn Kho</label>
                <div className="flex items-center justify-between">
                  <p className={`text-xl font-bold ${product.stockQuantity === 0 ? 'text-red-600' : 'text-gray-900'}`}>
                    {product.stockQuantity} sản phẩm
                  </p>
                  <button
                    onClick={() => {
                      setStockInput(product.stockQuantity.toString());
                      setShowStockModal(true);
                    }}
                    className="text-sm text-blue-600 hover:text-blue-800"
                  >
                    Cập nhật
                  </button>
                </div>
                {product.stockQuantity === 0 && (
                  <p className="text-sm text-red-600 mt-1">Hết hàng</p>
                )}
              </div>
            </div>
          </div>

          {/* Timestamps Card */}
          <div className="bg-white rounded-lg shadow-md p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Thời Gian</h2>
            <div className="space-y-3 text-sm">
              {product.createdAt && (
                <div>
                  <span className="text-gray-600">Ngày tạo:</span>
                  <p className="font-medium text-gray-900">
                    {new Date(product.createdAt).toLocaleString('vi-VN')}
                  </p>
                </div>
              )}
              {product.updatedAt && (
                <div>
                  <span className="text-gray-600">Cập nhật lần cuối:</span>
                  <p className="font-medium text-gray-900">
                    {new Date(product.updatedAt).toLocaleString('vi-VN')}
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Stock Update Modal */}
      {showStockModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">Cập Nhật Tồn Kho</h3>
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Số lượng mới
              </label>
              <input
                type="number"
                min="0"
                value={stockInput}
                onChange={(e) => setStockInput(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Nhập số lượng"
              />
            </div>
            <div className="flex justify-end space-x-3">
              <button
                onClick={() => {
                  setShowStockModal(false);
                  setStockInput('');
                }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Hủy
              </button>
              <button
                onClick={handleUpdateStock}
                disabled={updateStockMutation.isPending}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400"
              >
                {updateStockMutation.isPending ? 'Đang cập nhật...' : 'Cập nhật'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
