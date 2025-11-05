'use client';

/**
 * Admin Categories List Page
 * Displays categories in a hierarchical tree structure
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminCategoriesApi, Category } from '@/lib/api/admin-categories';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

export default function AdminCategoriesPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [expandedCategories, setExpandedCategories] = useState<Set<number>>(new Set());

  // Fetch category tree
  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-categories-tree'],
    queryFn: () => adminCategoriesApi.getTree(),
    refetchOnMount: 'always', // Always refetch when component mounts
    refetchOnWindowFocus: true, // Refetch when window gains focus
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminCategoriesApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-categories-tree'] });
      alert('Xóa danh mục thành công!');
    },
    onError: (error: any) => {
      alert('Lỗi: ' + (error.message || 'Không thể xóa danh mục'));
    },
  });

  const toggleExpand = (categoryId: number) => {
    setExpandedCategories((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(categoryId)) {
        newSet.delete(categoryId);
      } else {
        newSet.add(categoryId);
      }
      return newSet;
    });
  };

  const handleDelete = (id: number, name: string) => {
    if (window.confirm(`Bạn có chắc muốn xóa danh mục "${name}"?`)) {
      deleteMutation.mutate(id);
    }
  };

  const renderCategory = (category: Category, level: number = 0) => {
    const hasChildren = category.children && category.children.length > 0;
    const isExpanded = expandedCategories.has(category.id);
    const paddingLeft = level * 24;

    return (
      <div key={category.id} className="border-b border-gray-200">
        {/* Category Row */}
        <div
          className="flex items-center justify-between p-4 hover:bg-gray-50"
          style={{ paddingLeft: `${16 + paddingLeft}px` }}
        >
          <div className="flex items-center gap-3 flex-1">
            {/* Expand/Collapse Button */}
            {hasChildren && (
              <button
                onClick={() => toggleExpand(category.id)}
                className="p-1 hover:bg-gray-200 rounded"
              >
                <svg
                  className={`w-4 h-4 transition-transform ${isExpanded ? 'rotate-90' : ''}`}
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </button>
            )}
            {!hasChildren && <div className="w-6" />}

            {/* Category Image or Icon */}
            {category.imageUrl ? (
              <img
                src={category.imageUrl}
                alt={category.name}
                className="w-12 h-12 object-cover rounded"
              />
            ) : (
              <div className="w-12 h-12 bg-gray-100 rounded flex items-center justify-center">
                <svg className="w-6 h-6 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
                </svg>
              </div>
            )}

            {/* Category Info */}
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <h3 className="font-medium text-gray-900">{category.name}</h3>
                {!category.active && (
                  <span className="px-2 py-0.5 text-xs bg-red-100 text-red-800 rounded">
                    Không hoạt động
                  </span>
                )}
                {hasChildren && (
                  <span className="px-2 py-0.5 text-xs bg-blue-100 text-blue-800 rounded">
                    {category.childrenCount} con
                  </span>
                )}
              </div>
              <div className="text-sm text-gray-600 flex items-center gap-2">
                <span className="font-mono text-xs bg-gray-100 px-2 py-0.5 rounded">
                  {category.slug}
                </span>
                {category.description && (
                  <>
                    <span>•</span>
                    <span className="truncate max-w-md">{category.description}</span>
                  </>
                )}
              </div>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center gap-2">
            {/* View Details */}
            <button
              onClick={() => router.push(`/admin/categories/${category.id}`)}
              className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
              title="Chi tiết"
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
              </svg>
            </button>

            {/* Edit */}
            <button
              onClick={() => router.push(`/admin/categories/${category.id}/edit`)}
              className="p-2 text-orange-600 hover:bg-orange-50 rounded-lg transition-colors"
              title="Sửa"
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
            </button>

            {/* Delete */}
            <button
              onClick={() => handleDelete(category.id, category.name)}
              disabled={deleteMutation.isPending}
              className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50"
              title="Xóa"
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
            </button>
          </div>
        </div>

        {/* Children Categories */}
        {hasChildren && isExpanded && (
          <div>
            {category.children!.map((child) => renderCategory(child, level + 1))}
          </div>
        )}
      </div>
    );
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-600">Đang tải...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-red-600">Lỗi: {(error as Error).message}</div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Quản Lý Danh Mục</h1>
          <p className="text-gray-600">
            Tổng số: {data?.total || 0} danh mục gốc
          </p>
        </div>
        <button
          onClick={() => router.push('/admin/categories/new')}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 flex items-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Thêm Danh Mục
        </button>
      </div>

      {/* Category Tree */}
      <div className="bg-white rounded-lg shadow-md overflow-hidden">
        {data && data.categories.length > 0 ? (
          <div>
            {data.categories.map((category) => renderCategory(category))}
          </div>
        ) : (
          <div className="p-8 text-center text-gray-500">
            <svg className="w-16 h-16 mx-auto mb-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
            </svg>
            <p className="text-lg font-medium">Chưa có danh mục nào</p>
            <p className="text-sm">Click "Thêm Danh Mục" để tạo danh mục đầu tiên</p>
          </div>
        )}
      </div>

      {/* Helper Text */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
        <h3 className="font-medium text-blue-900 mb-2">Hướng dẫn:</h3>
        <ul className="text-sm text-blue-800 space-y-1 list-disc list-inside">
          <li>Click mũi tên để mở/đóng danh mục con</li>
          <li>Mỗi danh mục có thể có nhiều danh mục con (cấu trúc phân cấp)</li>
          <li>Số bên cạnh tên cho biết có bao nhiêu danh mục con</li>
          <li>Xóa danh mục sẽ là soft delete (đánh dấu không hoạt động)</li>
        </ul>
      </div>
    </div>
  );
}
