/**
 * Shop Page Client Component
 * Handles client-side interactions for product listing with client-side filtering
 */

'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { searchProductsWithFilters, getSaleProducts } from '@/lib/api/products';
import type { ProductListResponse } from '@/types/product';
import type { Category } from '@/types/category';
import ProductCard from '@/components/product/ProductCard';
import { useDebounce } from '@/hooks/useDebounce';

interface ShopPageClientProps {
  products: ProductListResponse;
  categories: Category[];
  availableSizes?: string[];
  availableColors?: string[];
  currentPage: number;
  filters: {
    category?: string;
    minPrice?: string;
    maxPrice?: string;
    brand?: string;
    search?: string;
    sort?: string;
    gender?: string;
    size?: string;
    color?: string;
  };
  title?: string;
  basePath?: string;
  forceBadge?: 'BEST SELLER' | 'MỚI' | 'SALE' | 'NỔI BẬT'; // Force all products to show specific badge
  isSaleMode?: boolean; // If true, use getSaleProducts API instead of searchProductsWithFilters
}

// Helper function to map color names to hex codes
const getColorHex = (colorName: string): string => {
  const colorMap: Record<string, string> = {
    'Đen': '#000000',
    'Trắng': '#FFFFFF',
    'Xám': '#9CA3AF',
    'Xám Đậm': '#4B5563',
    'Xanh': '#3B82F6', // Default blue
    'Xanh Navy': '#1E3A8A',
    'Xanh Dương': '#3B82F6',
    'Xanh Nhạt': '#ADD8E6',
    'Xanh Lá': '#10B981',
    'Xanh Rêu': '#6B8E23',
    'Đỏ': '#EF4444',
    'Đỏ Đô': '#DC143C',
    'Hồng': '#EC4899',
    'Hồng Pastel': '#FFD1DC',
    'Vàng': '#F59E0B',
    'Tím': '#8B5CF6',
    'Cam': '#F97316',
    'Be': '#F5F5DC',
    'Kem': '#FFFDD0',
    'Olive': '#808000',
  };
  return colorMap[colorName] || '#D1D5DB';
};

export default function ShopPageClient({
  products: initialProducts,
  categories,
  availableSizes = ['XS', 'S', 'M', 'L', 'XL', '2XL', '3XL'],
  availableColors = [],
  currentPage: initialPage,
  filters: initialFilters,
  title = 'Shop',
  basePath = '/shop',
  forceBadge,
  isSaleMode = false,
}: ShopPageClientProps) {
  const router = useRouter();

  // Local state for filters
  const [localFilters, setLocalFilters] = useState(initialFilters);
  const [currentPage, setCurrentPage] = useState(initialPage);

  // Collapsible filter sections
  const [expandedSections, setExpandedSections] = useState({
    gender: true,
    brand: false,
    size: false,
    color: false,
    price: false,
    category: false,
  });

  const toggleSection = (section: keyof typeof expandedSections) => {
    setExpandedSections(prev => ({
      ...prev,
      [section]: !prev[section]
    }));
  };

  // Fetch products with React Query (client-side)
  const { data: products = initialProducts, isFetching} = useQuery({
    queryKey: ['products', localFilters, currentPage, isSaleMode],
    queryFn: () => {
      // Sale mode: use dedicated getSaleProducts API
      if (isSaleMode) {
        return getSaleProducts(currentPage, 20);
      }

      // Normal mode: use search with filters
      // Find category by slug or ID to get the correct categoryId
      let categoryId: number | undefined = undefined;
      if (localFilters.category) {
        // Check if it's a number (ID) or string (slug)
        if (/^\d+$/.test(localFilters.category)) {
          // It's an ID
          categoryId = parseInt(localFilters.category);
        } else {
          // It's a slug, find the category ID
          const category = categories.find(c => c.slug === localFilters.category);
          categoryId = category?.id;
        }
      }

      return searchProductsWithFilters({
        page: currentPage,
        size: 4,
        categoryId,
        minPrice: localFilters.minPrice ? parseFloat(localFilters.minPrice) : undefined,
        maxPrice: localFilters.maxPrice ? parseFloat(localFilters.maxPrice) : undefined,
        brand: localFilters.brand,
        search: localFilters.search,
        gender: localFilters.gender,
        size_filter: localFilters.size,
        color: localFilters.color,
        sort: localFilters.sort || 'createdAt,desc',
      });
    },
    staleTime: 30000, // Cache for 30 seconds
    placeholderData: initialProducts, // Show previous data while loading
  });

  // Sync local filters with initial filters from server (when URL changes)
  useEffect(() => {
    setLocalFilters(initialFilters);
  }, [initialFilters]);

  // Update URL without reload - but skip on initial mount to prevent infinite loop
  useEffect(() => {
    // Build the new URL from local filters
    const params = new URLSearchParams();

    // Preserve sale mode param if it exists
    if (isSaleMode) {
      params.set('sale', 'true');
    }

    Object.entries(localFilters).forEach(([key, value]) => {
      if (value) {
        const paramKey = key === 'size' ? 'size_filter' : key;
        params.set(paramKey, value);
      }
    });

    if (currentPage > 0) {
      params.set('page', currentPage.toString());
    }

    const newUrl = `${basePath}${params.toString() ? `?${params.toString()}` : ''}`;

    // Only update URL if it's actually different from current URL
    // This prevents infinite loop when initialFilters changes from server
    const currentUrl = window.location.pathname + window.location.search;
    if (newUrl !== currentUrl) {
      router.replace(newUrl, { scroll: false });
    }
  }, [localFilters, currentPage, basePath, router, isSaleMode]);

  const updateFilters = (newFilters: Record<string, string | undefined>) => {
    setLocalFilters(prev => {
      const updated = { ...prev };
      Object.entries(newFilters).forEach(([key, value]) => {
        if (value) {
          updated[key as keyof typeof prev] = value;
        } else {
          delete updated[key as keyof typeof prev];
        }
      });
      return updated;
    });
    setCurrentPage(0); // Reset to first page when filters change
  };

  const changePage = (page: number) => {
    setCurrentPage(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // Search input state with debounce hook
  const [searchInput, setSearchInput] = useState(localFilters.search || '');
  const debouncedSearch = useDebounce(searchInput, 1000);

  // Update filters when debounced search changes
  useEffect(() => {
    if (debouncedSearch !== localFilters.search) {
      updateFilters({ search: debouncedSearch || undefined });
    }
  }, [debouncedSearch]);

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      {/* Header */}
      <div className="bg-white dark:bg-gray-800 border-b dark:border-gray-700">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white">{title}</h1>
          <p className="mt-2 text-gray-600 dark:text-gray-400">
            {products.totalElements} sản phẩm
          </p>

          {/* Search Bar */}
          <div className="mt-4">
            <div className="relative max-w-full">
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Tìm kiếm sản phẩm..."
                className="w-full px-4 py-3 pl-12 pr-12 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
              />
              <svg
                className="absolute left-4 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              {searchInput && (
                <button
                  onClick={() => setSearchInput('')}
                  className="absolute right-4 top-1/2 transform -translate-y-1/2 p-1.5 text-gray-500 hover:text-white hover:bg-red-500 bg-gray-200 dark:bg-gray-600 rounded-full transition-all duration-200"
                  title="Xóa tìm kiếm"
                >
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex gap-8">
          {/* Filters Sidebar */}
          <aside className="w-64 flex-shrink-0">
            <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                  Bộ Lọc
                </h2>
                <button
                  onClick={() => updateFilters({})}
                  className="text-sm text-blue-600 hover:text-blue-700"
                >
                  
                </button>
              </div>

              {/* Gender Filter - Collapsible */}
              <div className="border-b dark:border-gray-700">
                <button
                  onClick={() => toggleSection('gender')}
                  className="w-full flex items-center justify-between py-4 text-left"
                >
                  <h3 className="text-sm font-medium text-gray-900 dark:text-white">
                    Giới tính
                  </h3>
                  <svg
                    className={`w-5 h-5 text-gray-500 transition-transform ${expandedSections.gender ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>
                {expandedSections.gender && (
                  <div className="pb-4 space-y-2">
                    {['Nam', 'Nữ'].map((gender) => (
                      <label key={gender} className="flex items-center cursor-pointer">
                        <input
                          type="radio"
                          name="gender"
                          checked={localFilters.gender === gender}
                          onChange={() => updateFilters({ gender })}
                          className="w-4 h-4 text-blue-600 focus:ring-blue-500"
                        />
                        <span className="ml-2 text-sm text-gray-700 dark:text-gray-300">
                          {gender}
                        </span>
                      </label>
                    ))}
                  </div>
                )}
              </div>

              {/* Brand Filter - Collapsible */}
              <div className="border-b dark:border-gray-700">
                <button
                  onClick={() => toggleSection('brand')}
                  className="w-full flex items-center justify-between py-4 text-left"
                >
                  <h3 className="text-sm font-medium text-gray-900 dark:text-white">
                    Thương hiệu
                  </h3>
                  <svg
                    className={`w-5 h-5 text-gray-500 transition-transform ${expandedSections.brand ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>
                {expandedSections.brand && (
                  <div className="pb-4">
                    <div className="space-y-2 max-h-48 overflow-y-auto">
                      {['ActiveFit', 'BasicStyle', 'ElegantOffice', 'OutdoorPro', 'PoloClassic', 'StreetVibe'].map((brand) => {
                        const isSelected = localFilters.brand === brand;
                        return (
                          <label key={brand} className="flex items-center cursor-pointer">
                            <input
                              type="radio"
                              name="brand"
                              checked={isSelected}
                              onChange={() => updateFilters({ brand: isSelected ? undefined : brand })}
                              className="w-4 h-4 text-blue-600 focus:ring-blue-500"
                            />
                            <span className="ml-2 text-sm text-gray-700 dark:text-gray-300">
                              {brand}
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>

              {/* Size Filter - Collapsible */}
              <div className="border-b dark:border-gray-700">
                <button
                  onClick={() => toggleSection('size')}
                  className="w-full flex items-center justify-between py-4 text-left"
                >
                  <h3 className="text-sm font-medium text-gray-900 dark:text-white">
                    Kích thước
                  </h3>
                  <svg
                    className={`w-5 h-5 text-gray-500 transition-transform ${expandedSections.size ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>
                {expandedSections.size && (
                  <div className="pb-4">
                    <div className="grid grid-cols-3 gap-2">
                      {availableSizes.map((size) => (
                        <button
                          key={size}
                          onClick={() => updateFilters({ size: localFilters.size === size ? undefined : size })}
                          className={`py-2 px-3 text-sm border rounded-md transition-colors ${
                            localFilters.size === size
                              ? 'bg-gray-900 text-white border-gray-900 dark:bg-white dark:text-gray-900'
                              : 'bg-white text-gray-700 border-gray-300 hover:border-gray-400 dark:bg-gray-700 dark:text-gray-300 dark:border-gray-600'
                          }`}
                        >
                          {size}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Color Filter - Collapsible */}
              <div className="border-b dark:border-gray-700">
                <button
                  onClick={() => toggleSection('color')}
                  className="w-full flex items-center justify-between py-4 text-left"
                >
                  <h3 className="text-sm font-medium text-gray-900 dark:text-white">
                    Màu sắc
                  </h3>
                  <svg
                    className={`w-5 h-5 text-gray-500 transition-transform ${expandedSections.color ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>
                {expandedSections.color && (
                  <div className="pb-4">
                    <div className="flex flex-wrap gap-2">
                      {availableColors.map((colorName) => {
                        const colorHex = getColorHex(colorName);
                        const isSelected = localFilters.color === colorName;
                        return (
                          <button
                            key={colorName}
                            onClick={() => updateFilters({ color: isSelected ? undefined : colorName })}
                            className={`flex items-center gap-2 px-3 py-2 rounded-lg border transition-all ${
                              isSelected
                                ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/20'
                                : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-800'
                            }`}
                          >
                            <div
                              className={`w-6 h-6 rounded-full border-2 flex-shrink-0 ${
                                isSelected
                                  ? 'border-blue-600'
                                  : 'border-gray-300'
                              }`}
                              style={{ backgroundColor: colorHex }}
                            >
                              {colorHex === '#FFFFFF' && (
                                <div className="w-full h-full rounded-full border border-gray-200" />
                              )}
                            </div>
                            <span className={`text-xs font-medium whitespace-nowrap ${
                              isSelected
                                ? 'text-blue-600 dark:text-blue-400'
                                : 'text-gray-700 dark:text-gray-300'
                            }`}>
                              {colorName}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>

              {/* Price Range Filter - Collapsible */}
              <div className="border-b dark:border-gray-700">
                <button
                  onClick={() => toggleSection('price')}
                  className="w-full flex items-center justify-between py-4 text-left"
                >
                  <h3 className="text-sm font-medium text-gray-900 dark:text-white">
                    Giá
                  </h3>
                  <svg
                    className={`w-5 h-5 text-gray-500 transition-transform ${expandedSections.price ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>
                {expandedSections.price && (
                  <div className="pb-4 space-y-2">
                    {[
                      { label: '0 - 200.000đ', min: '0', max: '200000' },
                      { label: '200.000đ - 300.000đ', min: '200000', max: '300000' },
                      { label: '300.000đ - 500.000đ', min: '300000', max: '500000' },
                      { label: '>500.000đ', min: '500000', max: '' },
                    ].map((range) => {
                      // For ">500.000đ" range, check if minPrice matches and maxPrice is either empty string or undefined
                      const isChecked = range.max === ''
                        ? localFilters.minPrice === range.min && (!localFilters.maxPrice || localFilters.maxPrice === '')
                        : localFilters.minPrice === range.min && localFilters.maxPrice === range.max;

                      return (
                        <label key={range.label} className="flex items-center cursor-pointer">
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => {
                              if (isChecked) {
                                updateFilters({ minPrice: undefined, maxPrice: undefined });
                              } else {
                                updateFilters({ minPrice: range.min, maxPrice: range.max || undefined });
                              }
                            }}
                            className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                          />
                          <span className="ml-2 text-sm text-gray-700 dark:text-gray-300">
                            {range.label}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Category Filter - Collapsible */}
              <div className="border-b dark:border-gray-700">
                <button
                  onClick={() => toggleSection('category')}
                  className="w-full flex items-center justify-between py-4 text-left"
                >
                  <h3 className="text-sm font-medium text-gray-900 dark:text-white">
                    Nhóm sản phẩm
                  </h3>
                  <svg
                    className={`w-5 h-5 text-gray-500 transition-transform ${expandedSections.category ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>
                {expandedSections.category && (
                  <div className="pb-4">
                    <div className="space-y-2 max-h-48 overflow-y-auto">
                      {Array.isArray(categories) && categories.slice(0, 10).map((category) => {
                        const isSelected = localFilters.category === category.id.toString() || localFilters.category === category.slug;
                        return (
                          <label key={category.id} className="flex items-center cursor-pointer">
                            <input
                              type="radio"
                              name="category"
                              checked={isSelected}
                              onChange={() =>
                                // Always set the selected category slug (radio behavior)
                                updateFilters({ category: category.slug })
                              }
                              className="w-4 h-4 text-blue-600 focus:ring-blue-500"
                            />
                            <span className="ml-2 text-sm text-gray-700 dark:text-gray-300">
                              {category.name}
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>

              {/* Sort By */}
              <div className="pt-4">
                <h3 className="text-sm font-medium text-gray-900 dark:text-white mb-3">
                  Sắp xếp
                </h3>
                <select
                  value={localFilters.sort || 'createdAt,desc'}
                  onChange={(e) => updateFilters({ sort: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                >
                  <option value="createdAt,desc">Mới nhất</option>
                  <option value="price,asc">Giá: Thấp đến cao</option>
                  <option value="price,desc">Giá: Cao đến thấp</option>
                  <option value="soldCount,desc">Bán chạy nhất</option>
                  <option value="name,asc">Tên: A-Z</option>
                </select>
              </div>
            </div>
          </aside>

          {/* Product Grid */}
          <main className="flex-1">
            {/* Active Filters Tags */}
            {(localFilters.gender || localFilters.brand || localFilters.size || localFilters.color || localFilters.minPrice || localFilters.category || searchInput) && (
              <div className="mb-6 bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1">
                    <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">Bộ lọc đang áp dụng:</h3>
                    <div className="flex items-center gap-2 flex-wrap">
                      {searchInput && (
                        <span className="inline-flex items-center gap-2 px-3 py-1.5 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded-full text-sm font-medium">
                          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                          </svg>
                          &quot;{searchInput}&quot;
                          <button
                            onClick={() => setSearchInput('')}
                            className="hover:text-red-600 dark:hover:text-red-400 ml-1"
                            aria-label="Remove search filter"
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </span>
                      )}

                      {localFilters.gender && (
                        <span className="inline-flex items-center gap-2 px-3 py-1.5 bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300 rounded-full text-sm font-medium">
                          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                          </svg>
                          {localFilters.gender}
                          <button
                            onClick={() => updateFilters({ gender: undefined })}
                            className="hover:text-red-600 dark:hover:text-red-400 ml-1"
                            aria-label="Remove gender filter"
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </span>
                      )}

                      {localFilters.brand && (
                        <span className="inline-flex items-center gap-2 px-3 py-1.5 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 rounded-full text-sm font-medium">
                          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
                          </svg>
                          {localFilters.brand}
                          <button
                            onClick={() => updateFilters({ brand: undefined })}
                            className="hover:text-red-600 dark:hover:text-red-400 ml-1"
                            aria-label="Remove brand filter"
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </span>
                      )}

                      {localFilters.size && (
                        <span className="inline-flex items-center gap-2 px-3 py-1.5 bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 rounded-full text-sm font-medium">
                          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5l-5-5m5 5v-4m0 4h-4" />
                          </svg>
                          Size: {localFilters.size}
                          <button
                            onClick={() => updateFilters({ size: undefined })}
                            className="hover:text-red-600 dark:hover:text-red-400 ml-1"
                            aria-label="Remove size filter"
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </span>
                      )}

                      {localFilters.color && (
                        <span className="inline-flex items-center gap-2 px-3 py-1.5 bg-pink-100 dark:bg-pink-900/30 text-pink-700 dark:text-pink-300 rounded-full text-sm font-medium">
                          <div className="w-4 h-4 rounded-full border-2 border-current" style={{ backgroundColor: getColorHex(localFilters.color) }}></div>
                          {localFilters.color}
                          <button
                            onClick={() => updateFilters({ color: undefined })}
                            className="hover:text-red-600 dark:hover:text-red-400 ml-1"
                            aria-label="Remove color filter"
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </span>
                      )}

                      {localFilters.minPrice && (
                        <span className="inline-flex items-center gap-2 px-3 py-1.5 bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300 rounded-full text-sm font-medium">
                          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                          </svg>
                          {localFilters.minPrice === '0' && localFilters.maxPrice === '200000' && '0 - 200.000đ'}
                          {localFilters.minPrice === '200000' && localFilters.maxPrice === '300000' && '200.000đ - 300.000đ'}
                          {localFilters.minPrice === '300000' && localFilters.maxPrice === '500000' && '300.000đ - 500.000đ'}
                          {localFilters.minPrice === '500000' && (!localFilters.maxPrice || localFilters.maxPrice === '') && '>500.000đ'}
                          <button
                            onClick={() => updateFilters({ minPrice: undefined, maxPrice: undefined })}
                            className="hover:text-red-600 dark:hover:text-red-400 ml-1"
                            aria-label="Remove price filter"
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </span>
                      )}

                      {localFilters.category && (() => {
                        const foundCategory = categories.find(c => c.slug === localFilters.category) ||
                                             categories.find(c => c.id.toString() === localFilters.category);
                        const categoryName = foundCategory?.name || localFilters.category;

                        return (
                          <span className="inline-flex items-center gap-2 px-3 py-1.5 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 rounded-full text-sm font-medium">
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
                            </svg>
                            {categoryName}
                            <button
                              onClick={() => updateFilters({ category: undefined })}
                              className="hover:text-red-600 dark:hover:text-red-400 ml-1"
                              aria-label="Remove category filter"
                            >
                              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                              </svg>
                            </button>
                          </span>
                        );
                      })()}
                    </div>
                  </div>

                  <button
                    onClick={() => {
                      updateFilters({
                        gender: undefined,
                        size: undefined,
                        color: undefined,
                        minPrice: undefined,
                        maxPrice: undefined,
                        category: undefined,
                        search: undefined
                      });
                      setSearchInput('');
                    }}
                    className="flex items-center gap-2 px-4 py-2 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 rounded-lg hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors text-sm font-medium whitespace-nowrap"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                    Xoá tất cả
                  </button>
                </div>
              </div>
            )}

            {/* Loading Overlay */}
            {isFetching && (
              <div className="mb-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3 flex items-center gap-3">
                <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-600"></div>
                <span className="text-sm text-blue-700 dark:text-blue-300">Đang tải sản phẩm...</span>
              </div>
            )}

            {products.content.length === 0 ? (
              <div className="text-center py-12">
                <p className="text-gray-500 dark:text-gray-400">
                  Không tìm thấy sản phẩm nào phù hợp với bộ lọc của bạn.
                </p>
              </div>
            ) : (
              <>
                <div className={`grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 transition-opacity duration-200 ${isFetching ? 'opacity-50' : 'opacity-100'}`}>
                  {products.content.map((product) => (
                    <ProductCard key={product.id} product={product} badge={forceBadge} />
                  ))}
                </div>

                {/* Pagination - Always show when there are products */}
                {products.totalElements > 0 && (
                  <div className="mt-8 flex justify-center items-center gap-2">
                    <button
                      onClick={() => changePage(currentPage - 1)}
                      disabled={currentPage === 0}
                      className="px-4 py-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors font-medium text-gray-700 dark:text-gray-300"
                    >
                      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                      </svg>
                    </button>

                    {/* Page numbers */}
                    <div className="flex items-center gap-1">
                      {Array.from({ length: Math.min(5, products.totalPages) }, (_, i) => {
                        // Show first page, current page with neighbors, and last page
                        let pageNum;
                        if (products.totalPages <= 5) {
                          pageNum = i;
                        } else if (currentPage < 3) {
                          pageNum = i;
                        } else if (currentPage > products.totalPages - 4) {
                          pageNum = products.totalPages - 5 + i;
                        } else {
                          pageNum = currentPage - 2 + i;
                        }

                        return (
                          <button
                            key={pageNum}
                            onClick={() => changePage(pageNum)}
                            className={`w-10 h-10 rounded-lg font-medium transition-colors ${
                              currentPage === pageNum
                                ? 'bg-blue-600 text-white'
                                : 'bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
                            }`}
                          >
                            {pageNum + 1}
                          </button>
                        );
                      })}
                    </div>

                    <button
                      onClick={() => changePage(currentPage + 1)}
                      disabled={currentPage >= products.totalPages - 1}
                      className="px-4 py-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors font-medium text-gray-700 dark:text-gray-300"
                    >
                      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                      </svg>
                    </button>

                    <span className="ml-4 text-sm text-gray-600 dark:text-gray-400">
                      Trang {currentPage + 1} / {products.totalPages}
                    </span>
                  </div>
                )}
              </>
            )}
          </main>
        </div>
      </div>
    </div>
  );
}
