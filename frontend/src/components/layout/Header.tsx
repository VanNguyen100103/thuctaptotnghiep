/**
 * Header/Navbar Component (Coolmate Design)
 * Sticky header with search, cart, and user menu
 */

'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@/contexts/AuthContext';
import { getAllActiveCategories } from '@/lib/api/categories';
import { getCartItemCount } from '@/lib/api/cart';
import { getWishlist } from '@/lib/api/wishlist';
import CartDrawer from '@/components/cart/CartDrawer';
import SearchModal from '@/components/search/SearchModal';
import type { Category } from '@/types/category';

interface HeaderProps {
  announcementBar?: {
    text: string;
    link?: string;
  };
}

export default function Header({ announcementBar }: HeaderProps) {
  const { isAuthenticated, isAdmin, user, logout } = useAuth();
  const [showSearchModal, setShowSearchModal] = useState(false);
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [showCartDrawer, setShowCartDrawer] = useState(false);
  const [categories, setCategories] = useState<Category[]>([]);
  const [showMenMenu, setShowMenMenu] = useState(false);
  const [showWomenMenu, setShowWomenMenu] = useState(false);

  // Fetch cart count
  const { data: cartCountData } = useQuery({
    queryKey: ['cartCount'],
    queryFn: getCartItemCount,
    enabled: isAuthenticated,
    retry: false,
    staleTime: 30000, // Cache for 30 seconds
  });

  // Fetch wishlist count
  const { data: wishlistData } = useQuery({
    queryKey: ['wishlist'],
    queryFn: getWishlist,
    enabled: isAuthenticated,
    retry: false,
    staleTime: 30000, // Cache for 30 seconds
  });

  // Fetch categories on mount
  useEffect(() => {
    getAllActiveCategories()
      .then(data => setCategories(data))
      .catch(err => console.error('Failed to fetch categories:', err));
  }, []);

  // Get men's categories (parentId = 1 for "Thời Trang Nam")
  const menCategories = categories.filter(cat => cat.parentId === 1);

  // Get women's categories (parentId = 2 for "Thời Trang Nữ")
  const womenCategories = categories.filter(cat => cat.parentId === 2);

  const cartCount = cartCountData?.count || 0;
  const wishlistCount = wishlistData?.count || 0;

  return (
    <header className="sticky top-0 z-50 bg-white shadow-sm">
      {/* Announcement Bar */}
      {announcementBar && (
        <div className="bg-black text-white text-center py-2 px-4 text-sm">
          {announcementBar.link ? (
            <Link href={announcementBar.link} className="hover:underline">
              {announcementBar.text}
            </Link>
          ) : (
            <span>{announcementBar.text}</span>
          )}
        </div>
      )}

      {/* Main Header */}
      <div className="bg-white border-b border-gray-100">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            {/* Logo */}
            <Link href="/" className="flex items-center">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 bg-black text-white flex items-center justify-center font-bold text-xl">
                  ⚡
                </div>
                <span className="text-xl font-black hidden sm:block">COOLMATE</span>
              </div>
            </Link>

            {/* Desktop Navigation */}
            <nav className="hidden md:flex items-center space-x-8">
              <Link href="/new" className="text-sm font-medium hover:text-blue-600 transition-colors">
                NEW
              </Link>

              {/* Men's Menu with Dropdown (Click) */}
              <div className="relative h-16 flex items-center">
                <button
                  onClick={() => {
                    setShowMenMenu(!showMenMenu);
                    setShowWomenMenu(false);
                  }}
                  className="text-sm font-medium hover:text-blue-600 transition-colors flex items-center gap-1"
                >
                  NAM
                  <svg
                    className={`w-4 h-4 transition-transform ${showMenMenu ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>

                {/* Men's Mega Menu */}
                {showMenMenu && menCategories.length > 0 && (
                  <>
                    {/* Backdrop */}
                    <div
                      className="fixed inset-0 bg-black/20 z-40"
                      onClick={() => setShowMenMenu(false)}
                    />
                    <div className="absolute top-full left-0 mt-0 w-[600px] bg-white shadow-2xl rounded-b-lg border border-gray-200 z-50">
                      <div className="p-6">
                        {/* View All Link */}
                        <div className="mb-4 pb-4 border-b border-gray-200">
                          <Link
                            href="/category/nam"
                            className="inline-flex items-center gap-2 text-base font-bold text-blue-600 hover:text-blue-700"
                            onClick={() => setShowMenMenu(false)}
                          >
                            <span>Xem tất cả</span>
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                            </svg>
                          </Link>
                        </div>

                        {/* Category Grid */}
                        <div className="grid grid-cols-2 gap-3">
                          {menCategories.map((category) => (
                            <Link
                              key={category.id}
                              href={`/category/nam?category=${category.slug}`}
                              className="group p-3 rounded-lg hover:bg-blue-50 transition-all"
                              onClick={() => setShowMenMenu(false)}
                            >
                              <div className="flex items-center gap-3">
                                {category.imageUrl && (
                                  <img
                                    src={category.imageUrl}
                                    alt={category.name}
                                    className="w-12 h-12 object-cover rounded-md flex-shrink-0"
                                  />
                                )}
                                <div className="flex-1 min-w-0">
                                  <p className="text-sm font-semibold text-gray-900 group-hover:text-blue-600 mb-1 truncate">
                                    {category.name}
                                  </p>
                                  {category.productCount !== undefined && (
                                    <p className="text-xs text-gray-500">
                                      {category.productCount} sản phẩm
                                    </p>
                                  )}
                                </div>
                              </div>
                            </Link>
                          ))}
                        </div>
                      </div>
                    </div>
                  </>
                )}
              </div>

              {/* Women's Menu with Dropdown (Click) */}
              <div className="relative h-16 flex items-center">
                <button
                  onClick={() => {
                    setShowWomenMenu(!showWomenMenu);
                    setShowMenMenu(false);
                  }}
                  className="text-sm font-medium hover:text-pink-600 transition-colors flex items-center gap-1"
                >
                  NỮ
                  <svg
                    className={`w-4 h-4 transition-transform ${showWomenMenu ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>

                {/* Women's Mega Menu */}
                {showWomenMenu && womenCategories.length > 0 && (
                  <>
                    {/* Backdrop */}
                    <div
                      className="fixed inset-0 bg-black/20 z-40"
                      onClick={() => setShowWomenMenu(false)}
                    />
                    <div className="absolute top-full left-0 mt-0 w-[600px] bg-white shadow-2xl rounded-b-lg border border-gray-200 z-50">
                      <div className="p-6">
                        {/* View All Link */}
                        <div className="mb-4 pb-4 border-b border-gray-200">
                          <Link
                            href="/category/nu"
                            className="inline-flex items-center gap-2 text-base font-bold text-pink-600 hover:text-pink-700"
                            onClick={() => setShowWomenMenu(false)}
                          >
                            <span>Xem tất cả</span>
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                            </svg>
                          </Link>
                        </div>

                        {/* Category Grid */}
                        <div className="grid grid-cols-2 gap-3">
                          {womenCategories.map((category) => (
                            <Link
                              key={category.id}
                              href={`/category/nu?category=${category.slug}`}
                              className="group p-3 rounded-lg hover:bg-pink-50 transition-all"
                              onClick={() => setShowWomenMenu(false)}
                            >
                              <div className="flex items-center gap-3">
                                {category.imageUrl && (
                                  <img
                                    src={category.imageUrl}
                                    alt={category.name}
                                    className="w-12 h-12 object-cover rounded-md flex-shrink-0"
                                  />
                                )}
                                <div className="flex-1 min-w-0">
                                  <p className="text-sm font-semibold text-gray-900 group-hover:text-pink-600 mb-1 truncate">
                                    {category.name}
                                  </p>
                                  {category.productCount !== undefined && (
                                    <p className="text-xs text-gray-500">
                                      {category.productCount} sản phẩm
                                    </p>
                                  )}
                                </div>
                              </div>
                            </Link>
                          ))}
                        </div>
                      </div>
                    </div>
                  </>
                )}
              </div>

              <Link href="/category/the-thao" className="text-sm font-medium hover:text-blue-600 transition-colors">
                THỂ THAO
              </Link>
              <Link href="/shop?sale=true" className="text-sm font-medium text-red-600 hover:text-red-700 transition-colors relative">
                SALE
                <span className="absolute -top-3 left-8 text-xs bg-red-600 text-white px-1 rounded scale-75">HOT</span>
              </Link>
              <Link href="/care-and-share" className="text-sm font-medium hover:text-blue-600 transition-colors">
                C&S
              </Link>
            </nav>

            {/* Right Actions */}
            <div className="flex items-center gap-4">
              {/* Search */}
              <button
                onClick={() => setShowSearchModal(true)}
                className="p-2 hover:bg-gray-100 rounded-full transition-colors"
                aria-label="Search"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
              </button>

              {/* Wishlist */}
              {isAuthenticated && (
                <Link
                  href="/wishlist"
                  className="p-2 hover:bg-gray-100 rounded-full transition-colors relative"
                  aria-label="Wishlist"
                >
                  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                  </svg>
                  {wishlistCount > 0 && (
                    <span className="absolute -top-1 -right-1 w-5 h-5 bg-red-600 text-white text-xs rounded-full flex items-center justify-center">
                      {wishlistCount > 99 ? '99+' : wishlistCount}
                    </span>
                  )}
                </Link>
              )}

              {/* Cart */}
              {isAuthenticated && (
                <button
                  onClick={() => setShowCartDrawer(true)}
                  className="p-2 hover:bg-gray-100 rounded-full transition-colors relative"
                  aria-label="Cart"
                >
                  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
                  </svg>
                  {cartCount > 0 && (
                    <span className="absolute -top-1 -right-1 w-5 h-5 bg-red-600 text-white text-xs rounded-full flex items-center justify-center">
                      {cartCount > 99 ? '99+' : cartCount}
                    </span>
                  )}
                </button>
              )}

              {/* User Menu */}
              <div className="relative">
                {isAuthenticated ? (
                  <>
                    <button
                      onClick={() => setShowUserMenu(!showUserMenu)}
                      className="hover:opacity-80 transition-opacity"
                      aria-label="User menu"
                    >
                      {user?.avatarUrl ? (
                        <img
                          src={user.avatarUrl}
                          alt={user.username || 'User'}
                          className="w-8 h-8 rounded-full object-cover border-2 border-blue-500"
                        />
                      ) : (
                        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-bold text-sm">
                          {user?.username?.[0]?.toUpperCase() || 'U'}
                        </div>
                      )}
                    </button>

                    {showUserMenu && (
                      <div className="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-lg border border-gray-100 py-2">
                        <div className="px-4 py-2 border-b border-gray-100 flex items-center gap-3">
                          {user?.avatarUrl ? (
                            <img
                              src={user.avatarUrl}
                              alt={user.username || 'User'}
                              className="w-10 h-10 rounded-full object-cover"
                            />
                          ) : (
                            <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-bold">
                              {user?.username?.[0]?.toUpperCase() || 'U'}
                            </div>
                          )}
                          <div className="flex-1 min-w-0">
                            <p className="text-sm font-medium text-gray-900 truncate">{user?.username}</p>
                          </div>
                        </div>
                        <Link
                          href="/profile"
                          className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
                          onClick={() => setShowUserMenu(false)}
                        >
                          Hồ Sơ
                        </Link>
                        <Link
                          href="/orders"
                          className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
                          onClick={() => setShowUserMenu(false)}
                        >
                          Đơn Hàng
                        </Link>
                        {isAdmin && (
                          <Link
                            href="/admin"
                            className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
                            onClick={() => setShowUserMenu(false)}
                          >
                            Quản Trị
                          </Link>
                        )}
                        <button
                          onClick={() => {
                            setShowUserMenu(false);
                            logout();
                          }}
                          className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50"
                        >
                          Đăng Xuất
                        </button>
                      </div>
                    )}
                  </>
                ) : (
                  <Link
                    href="/login"
                    className="p-2  rounded-full transition-colors"
                    aria-label="Login"
                  >
                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                    </svg>
                  </Link>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Cart Drawer */}
      <CartDrawer isOpen={showCartDrawer} onClose={() => setShowCartDrawer(false)} />

      {/* Search Modal */}
      <SearchModal isOpen={showSearchModal} onClose={() => setShowSearchModal(false)} />
    </header>
  );
}
