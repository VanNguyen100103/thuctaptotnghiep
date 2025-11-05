'use client';

import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { getCurrentUser, deleteUserAccount } from '@/lib/api/users';
import type { UserProfile } from '@/types/user';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

export default function ProfilePage() {
  const { isAuthenticated, loading: authLoading, logout } = useAuth();
  const router = useRouter();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.push('/login?redirect=/profile');
      return;
    }

    if (isAuthenticated) {
      loadProfile();
    }
  }, [isAuthenticated, authLoading, router]);

  const loadProfile = async () => {
    try {
      setLoading(true);
      const data = await getCurrentUser();
      console.log('[ProfilePage] Loaded profile:', data);
      setProfile(data);
    } catch (err: any) {
      console.error('[ProfilePage] Failed to load profile:', err);
      setError('Không thể tải thông tin hồ sơ');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteAccount = async () => {
    if (!profile) return;

    setDeleting(true);
    try {
      const response = await deleteUserAccount(profile.id);
      console.log('[ProfilePage] Account deleted:', response);

      // Show success message
      alert('Tài khoản của bạn đã được vô hiệu hóa thành công. Bạn có thể bật 2FA để mở khóa lại tài khoản.');

      // Reload page to show updated status
      window.location.reload();

    } catch (err: any) {
      console.error('[ProfilePage] Failed to delete account:', err);
      alert('Không thể xóa tài khoản. Vui lòng thử lại.');
      setDeleting(false);
      setShowDeleteModal(false);
    }
  };

  if (authLoading || loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-600 mb-4">{error}</p>
          <button
            onClick={loadProfile}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            Thử lại
          </button>
        </div>
      </div>
    );
  }

  if (!profile) {
    return null;
  }

  const displayName = profile.firstName && profile.lastName
    ? `${profile.firstName} ${profile.lastName}`
    : profile.fullName || profile.username;

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
          <div className="flex items-center gap-6">
            {/* Avatar */}
            <div className="relative">
              {profile.avatarUrl ? (
                <img
                  src={profile.avatarUrl}
                  alt={displayName}
                  className="w-24 h-24 rounded-full object-cover border-4 border-blue-500"
                />
              ) : (
                <div className="w-24 h-24 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-bold text-3xl">
                  {profile.username?.[0]?.toUpperCase() || 'U'}
                </div>
              )}
            </div>

            {/* User Info */}
            <div className="flex-1">
              <h1 className="text-2xl font-bold text-gray-900 mb-1">{displayName}</h1>
              <p className="text-gray-600 mb-2">@{profile.username}</p>
              <p className="text-sm text-gray-500">{profile.email}</p>
            </div>

            {/* Edit Button */}
            <Link
              href="/profile/edit"
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              Chỉnh sửa
            </Link>
          </div>
        </div>

        {/* Profile Details */}
        <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
          <h2 className="text-xl font-bold text-gray-900 mb-4">Thông tin cá nhân</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Họ</label>
              <p className="text-gray-900">{profile.firstName || '-'}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Tên</label>
              <p className="text-gray-900">{profile.lastName || '-'}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <p className="text-gray-900">{profile.email}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Số điện thoại</label>
              <p className="text-gray-900">{profile.phoneNumber || profile.phone || '-'}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Giới tính</label>
              <p className="text-gray-900">
                {profile.gender === 'MALE' ? 'Nam' : profile.gender === 'FEMALE' ? 'Nữ' : profile.gender === 'OTHER' ? 'Khác' : '-'}
              </p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Ngày sinh</label>
              <p className="text-gray-900">
                {profile.dateOfBirth ? new Date(profile.dateOfBirth).toLocaleDateString('vi-VN') : '-'}
              </p>
            </div>
          </div>
        </div>

        {/* Security */}
        <div className="bg-white rounded-lg shadow-sm p-6">
          <h2 className="text-xl font-bold text-gray-900 mb-4">Bảo mật</h2>
          <div className="space-y-4">
            <div className="flex items-center justify-between py-3 border-b border-gray-200">
              <div>
                <p className="font-medium text-gray-900">Xác thực 2 yếu tố (2FA)</p>
                <p className="text-sm text-gray-600">Mở lại tài khoản của bạn với xác thực 2 lớp</p>
              </div>
              <div className="flex items-center gap-3">
                {profile.twoFactorEnabled ? (
                  <span className="px-3 py-1 bg-green-100 text-green-800 text-sm font-medium rounded-full">
                    Đã bật
                  </span>
                ) : (
                  <span className="px-3 py-1 bg-gray-100 text-gray-800 text-sm font-medium rounded-full">
                    Chưa bật
                  </span>
                )}
                <Link
                  href="/profile/security"
                  className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Quản lý
                </Link>
              </div>
            </div>

            <div className="flex items-center justify-between py-3">
              <div>
                <p className="font-medium text-gray-900">Mật khẩu</p>
                <p className="text-sm text-gray-600">Thay đổi mật khẩu của bạn</p>
              </div>
              <Link
                href="/profile/change-password"
                className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Đổi mật khẩu
              </Link>
            </div>
          </div>
        </div>

        {/* Account Info */}
        <div className="mt-6 bg-white rounded-lg shadow-sm p-6">
          <h2 className="text-xl font-bold text-gray-900 mb-4">Thông tin tài khoản</h2>
          <div className="space-y-3">
            <div className="flex items-center justify-between py-2">
              <span className="text-gray-600">Trạng thái</span>
              <span className={`px-3 py-1 rounded-full text-sm font-medium ${
                profile.enabled ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
              }`}>
                {profile.enabled ? 'Đang hoạt động' : 'Đã vô hiệu hóa'}
              </span>
            </div>
            {profile.createdAt && (
              <div className="flex items-center justify-between py-2">
                <span className="text-gray-600">Ngày tạo</span>
                <span className="text-gray-900">
                  {new Date(profile.createdAt).toLocaleDateString('vi-VN')}
                </span>
              </div>
            )}
            {profile.updatedAt && (
              <div className="flex items-center justify-between py-2">
                <span className="text-gray-600">Cập nhật lần cuối</span>
                <span className="text-gray-900">
                  {new Date(profile.updatedAt).toLocaleDateString('vi-VN')}
                </span>
              </div>
            )}
          </div>
        </div>

        {/* Danger Zone */}
        <div className="mt-6 bg-white rounded-lg shadow-sm p-6 border-2 border-red-200">
          <h2 className="text-xl font-bold text-red-600 mb-4">Vùng nguy hiểm</h2>
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-gray-900">Xóa tài khoản</p>
              <p className="text-sm text-gray-600">Vô hiệu hóa tài khoản của bạn vĩnh viễn. Hành động này không thể hoàn tác.</p>
            </div>
            <button
              onClick={() => setShowDeleteModal(true)}
              className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
            >
              Xóa tài khoản
            </button>
          </div>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0  bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg max-w-md w-full p-6">
            <div className="flex items-center mb-4">
              <div className="w-12 h-12 rounded-full bg-red-100 flex items-center justify-center mr-4">
                <svg className="w-6 h-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
              </div>
              <h3 className="text-xl font-bold text-gray-900">Xác nhận xóa tài khoản</h3>
            </div>

            <p className="text-gray-600 mb-6">
              Bạn có chắc chắn muốn xóa tài khoản của mình không? Hành động này sẽ:
            </p>

            <ul className="list-disc list-inside text-gray-600 mb-6 space-y-2">
              <li>Vô hiệu hóa tài khoản của bạn</li>
              <li>Xóa avatar khỏi hệ thống</li>
              <li>Bạn sẽ không thể đăng nhập lại</li>
              <li>Hành động này không thể hoàn tác</li>
            </ul>

            <div className="flex gap-4">
              <button
                onClick={handleDeleteAccount}
                disabled={deleting}
                className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {deleting ? (
                  <span className="flex items-center justify-center">
                    <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Đang xóa...
                  </span>
                ) : (
                  'Xóa tài khoản'
                )}
              </button>
              <button
                onClick={() => setShowDeleteModal(false)}
                disabled={deleting}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50 transition-colors"
              >
                Hủy
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
