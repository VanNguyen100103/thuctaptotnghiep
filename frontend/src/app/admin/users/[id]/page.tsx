'use client';

/**
 * User Detail Page
 * View and edit user details
 */

import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminUsersApi } from '@/lib/api/admin';
import { useState, useEffect } from 'react';

export default function UserDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const userId = parseInt(params.id as string);

  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    phoneNumber: '',
    enabled: true,
    accountNonLocked: true,
    roles: [] as string[],
  });

  // Fetch user details
  const { data: user, isLoading } = useQuery({
    queryKey: ['admin-user', userId],
    queryFn: () => adminUsersApi.getById(userId),
  });

  // Populate form data when user data is loaded
  useEffect(() => {
    if (user) {
      // Normalize roles to always have ROLE_ prefix for consistent UI handling
      const normalizedRoles = user.roles.map(role =>
        role.startsWith('ROLE_') ? role : `ROLE_${role}`
      );

      setFormData({
        username: user.username,
        email: user.email,
        firstName: user.firstName || '',
        lastName: user.lastName || '',
        phoneNumber: user.phoneNumber || '',
        enabled: user.enabled,
        accountNonLocked: user.accountNonLocked,
        roles: normalizedRoles,
      });
    }
  }, [user]);

  // Update user mutation
  const updateMutation = useMutation({
    mutationFn: (data: typeof formData) => adminUsersApi.update(userId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-user', userId] });
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      setIsEditing(false);
      alert('Cập nhật thành công!');
    },
    onError: (error: any) => {
      alert('Lỗi: ' + (error.message || 'Không thể cập nhật người dùng'));
    },
  });

  // Toggle status mutation
  const toggleStatusMutation = useMutation({
    mutationFn: (enabled: boolean) => adminUsersApi.updateStatus(userId, enabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-user', userId] });
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
  });

  // Reset password mutation
  const resetPasswordMutation = useMutation({
    mutationFn: (newPassword: string) => adminUsersApi.resetPassword(userId, newPassword),
    onSuccess: () => {
      alert('Mật khẩu đã được reset thành công!');
    },
    onError: (error: any) => {
      alert('Lỗi: ' + (error.message || 'Không thể reset mật khẩu'));
    },
  });

  // Update roles mutation
  const updateRolesMutation = useMutation({
    mutationFn: (roles: string[]) => adminUsersApi.updateRoles(userId, roles),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-user', userId] });
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      alert('Cập nhật vai trò thành công!');
    },
    onError: (error: any) => {
      alert('Lỗi: ' + (error.message || 'Không thể cập nhật vai trò'));
    },
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-600">Đang tải...</div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-red-600">Không tìm thấy người dùng</div>
      </div>
    );
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateMutation.mutate(formData);
  };

  const handleToggleStatus = () => {
    if (confirm(`Bạn có chắc muốn ${user.enabled ? 'vô hiệu hóa' : 'kích hoạt'} người dùng này?`)) {
      toggleStatusMutation.mutate(!user.enabled);
    }
  };

  const handleResetPassword = () => {
    const newPassword = prompt('Nhập mật khẩu mới (ít nhất 8 ký tự):');
    if (newPassword && newPassword.length >= 8) {
      resetPasswordMutation.mutate(newPassword);
    } else if (newPassword !== null) {
      alert('Mật khẩu phải có ít nhất 8 ký tự');
    }
  };

  const handleRoleToggle = (role: string) => {
    const currentRoles = formData.roles;
    const newRoles = currentRoles.includes(role)
      ? currentRoles.filter((r) => r !== role)
      : [...currentRoles, role];

    if (newRoles.length === 0) {
      alert('Phải có ít nhất một vai trò');
      return;
    }

    setFormData({ ...formData, roles: newRoles });
  };

  const handleUpdateRoles = () => {
    if (formData.roles.length === 0) {
      alert('Phải có ít nhất một vai trò');
      return;
    }
    // Backend expects roles without ROLE_ prefix
    const rolesWithoutPrefix = formData.roles.map(role => role.replace('ROLE_', ''));
    updateRolesMutation.mutate(rolesWithoutPrefix);
  };

  return (
    <div className="space-y-6">
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
            <h1 className="text-2xl font-bold text-gray-900">Chi Tiết Người Dùng</h1>
            <p className="text-gray-600">ID: {user.id}</p>
          </div>
        </div>

        <div className="flex space-x-3">
          {!isEditing && (
            <>
              <button
                onClick={() => setIsEditing(true)}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                Chỉnh Sửa
              </button>
              <button
                onClick={handleToggleStatus}
                className={`px-4 py-2 rounded-lg ${
                  user.enabled
                    ? 'bg-orange-100 text-orange-700 hover:bg-orange-200'
                    : 'bg-green-100 text-green-700 hover:bg-green-200'
                }`}
              >
                {user.enabled ? 'Vô Hiệu Hóa' : 'Kích Hoạt'}
              </button>
              <button
                onClick={handleResetPassword}
                className="px-4 py-2 bg-purple-100 text-purple-700 rounded-lg hover:bg-purple-200"
              >
                Reset Mật Khẩu
              </button>
            </>
          )}
        </div>
      </div>

      {/* User Info Card */}
      <div className="bg-white rounded-lg shadow-md p-6">
        {/* Avatar Section */}
        <div className="flex items-center space-x-6 mb-6 pb-6 border-b">
          <div className="h-24 w-24 flex-shrink-0">
            {user.avatarUrl ? (
              <img
                src={user.avatarUrl}
                alt={user.username}
                className="h-24 w-24 rounded-full object-cover border-4 border-gray-200"
              />
            ) : (
              <div className="h-24 w-24 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-bold text-3xl shadow-lg">
                {user.username[0].toUpperCase()}
              </div>
            )}
          </div>
          <div>
            <h2 className="text-2xl font-bold text-gray-900">{user.username}</h2>
            <p className="text-gray-600">{user.email}</p>
            <div className="flex space-x-2 mt-2">
              <span
                className={`px-3 py-1 rounded-full text-sm font-medium ${
                  user.enabled
                    ? 'bg-green-100 text-green-800'
                    : 'bg-red-100 text-red-800'
                }`}
              >
                {user.enabled ? 'Hoạt động' : 'Vô hiệu hóa'}
              </span>
              {user.accountNonLocked ? (
                <span className="px-3 py-1 rounded-full text-sm font-medium bg-blue-100 text-blue-800">
                  Không khóa
                </span>
              ) : (
                <span className="px-3 py-1 rounded-full text-sm font-medium bg-orange-100 text-orange-800">
                  Đã khóa
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit}>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Username */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Tên đăng nhập
              </label>
              <input
                type="text"
                value={formData.username}
                disabled
                className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50"
              />
            </div>

            {/* Email */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Email
              </label>
              <input
                type="email"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                disabled={!isEditing}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg disabled:bg-gray-50"
              />
            </div>

            {/* First Name */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Họ
              </label>
              <input
                type="text"
                value={formData.firstName}
                onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                disabled={!isEditing}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg disabled:bg-gray-50"
              />
            </div>

            {/* Last Name */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Tên
              </label>
              <input
                type="text"
                value={formData.lastName}
                onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                disabled={!isEditing}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg disabled:bg-gray-50"
              />
            </div>

            {/* Phone */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Số điện thoại
              </label>
              <input
                type="text"
                value={formData.phoneNumber}
                onChange={(e) => setFormData({ ...formData, phoneNumber: e.target.value })}
                disabled={!isEditing}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg disabled:bg-gray-50"
              />
            </div>

            {/* Roles */}
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-3">
                Vai trò
              </label>
              {isEditing ? (
                <div className="space-y-3">
                  <div className="space-y-2">
                    <label className="flex items-center space-x-3 p-3 border border-gray-300 rounded-lg hover:bg-gray-50 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={formData.roles.includes('ROLE_USER')}
                        onChange={() => handleRoleToggle('ROLE_USER')}
                        className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                      />
                      <div>
                        <div className="font-medium text-gray-900">Người dùng</div>
                        <div className="text-sm text-gray-600">Quyền truy cập cơ bản</div>
                      </div>
                    </label>

                    <label className="flex items-center space-x-3 p-3 border border-gray-300 rounded-lg hover:bg-gray-50 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={formData.roles.includes('ROLE_ADMIN')}
                        onChange={() => handleRoleToggle('ROLE_ADMIN')}
                        className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                      />
                      <div>
                        <div className="font-medium text-gray-900">Quản trị viên</div>
                        <div className="text-sm text-gray-600">Quyền quản lý toàn bộ hệ thống</div>
                      </div>
                    </label>

                    <label className="flex items-center space-x-3 p-3 border border-gray-300 rounded-lg hover:bg-gray-50 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={formData.roles.includes('ROLE_MODERATOR')}
                        onChange={() => handleRoleToggle('ROLE_MODERATOR')}
                        className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                      />
                      <div>
                        <div className="font-medium text-gray-900">Điều hành viên</div>
                        <div className="text-sm text-gray-600">Quyền kiểm duyệt nội dung</div>
                      </div>
                    </label>
                  </div>
                  <button
                    type="button"
                    onClick={handleUpdateRoles}
                    disabled={updateRolesMutation.isPending}
                    className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:bg-gray-400 text-sm"
                  >
                    {updateRolesMutation.isPending ? 'Đang cập nhật...' : 'Cập nhật vai trò'}
                  </button>
                </div>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {user.roles.map((role) => {
                    // Normalize role display by removing ROLE_ prefix if present
                    const displayRole = role.startsWith('ROLE_')
                      ? role.replace('ROLE_', '')
                      : role;
                    return (
                      <span
                        key={role}
                        className="px-3 py-1 bg-purple-100 text-purple-800 rounded-full text-sm font-medium"
                      >
                        {displayRole}
                      </span>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

          {/* Metadata */}
          <div className="mt-6 pt-6 border-t grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
            <div>
              <span className="text-gray-600">Số lần đăng nhập thất bại:</span>
              <span className="ml-2 font-medium text-gray-900">{user.failedLoginAttempts}</span>
            </div>
            <div>
              <span className="text-gray-600">Ngày tạo:</span>
              <span className="ml-2 font-medium text-gray-900">
                {user.createdAt ? new Date(user.createdAt).toLocaleDateString('vi-VN') : 'N/A'}
              </span>
            </div>
            <div>
              <span className="text-gray-600">Cập nhật lần cuối:</span>
              <span className="ml-2 font-medium text-gray-900">
                {user.updatedAt ? new Date(user.updatedAt).toLocaleDateString('vi-VN') : 'N/A'}
              </span>
            </div>
          </div>

          {/* Action Buttons */}
          {isEditing && (
            <div className="mt-6 flex justify-end space-x-3">
              <button
                type="button"
                onClick={() => {
                  setIsEditing(false);
                  setFormData({
                    username: user.username,
                    email: user.email,
                    firstName: user.firstName || '',
                    lastName: user.lastName || '',
                    phoneNumber: user.phoneNumber || '',
                    enabled: user.enabled,
                    accountNonLocked: user.accountNonLocked,
                    roles: user.roles,
                  });
                }}
                className="px-6 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Hủy
              </button>
              <button
                type="submit"
                disabled={updateMutation.isPending}
                className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400"
              >
                {updateMutation.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
              </button>
            </div>
          )}
        </form>
      </div>
    </div>
  );
}
