'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { getTwoFactorStatus, enableTwoFactor, verifyAndActivateTwoFactor, disableTwoFactor, regenerateBackupCodes } from '@/lib/api/twoFactor';
import type { TwoFactorSetupResponse } from '@/lib/api/twoFactor';

export default function SecuritySettingsPage() {
  const [loading, setLoading] = useState(true);
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(false);

  // Setup modal states
  const [showSetupModal, setShowSetupModal] = useState(false);
  const [setupData, setSetupData] = useState<TwoFactorSetupResponse | null>(null);
  const [verificationCode, setVerificationCode] = useState('');
  const [setupStep, setSetupStep] = useState<'qr' | 'verify' | 'backup'>('qr');

  // Disable modal states
  const [showDisableModal, setShowDisableModal] = useState(false);
  const [disableCode, setDisableCode] = useState('');

  // Regenerate backup codes modal
  const [showRegenerateModal, setShowRegenerateModal] = useState(false);
  const [regenerateCode, setRegenerateCode] = useState('');
  const [newBackupCodes, setNewBackupCodes] = useState<string[]>([]);

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Check 2FA status on mount
  useEffect(() => {
    checkTwoFactorStatus();
  }, []);

  const checkTwoFactorStatus = async () => {
    try {
      const status = await getTwoFactorStatus();
      setTwoFactorEnabled(status.enabled);
      setLoading(false);
    } catch (err) {
      console.error('Failed to check 2FA status:', err);
      setError('Không thể tải trạng thái 2FA');
      setLoading(false);
    }
  };

  const handleEnableTwoFactor = async () => {
    try {
      setError('');
      setSuccess('');
      const response = await enableTwoFactor();
      setSetupData(response);
      setShowSetupModal(true);
      setSetupStep('qr');
    } catch (err: any) {
      setError(err.response?.data?.error || 'Không thể bật 2FA');
    }
  };

  const handleVerifyCode = async () => {
    if (!verificationCode || verificationCode.length !== 6) {
      setError('Vui lòng nhập mã 6 chữ số hợp lệ');
      return;
    }

    try {
      setError('');
      await verifyAndActivateTwoFactor(verificationCode);
      setSetupStep('backup');
      setTwoFactorEnabled(true);
      setSuccess('Đã bật 2FA thành công!');

      // Reload the page to refresh user profile data
      setTimeout(() => {
        window.location.reload();
      }, 2000);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Mã xác minh không hợp lệ');
    }
  };

  const handleDisableTwoFactor = async () => {
    if (!disableCode || disableCode.length !== 6) {
      setError('Vui lòng nhập mã 6 chữ số hợp lệ');
      return;
    }

    try {
      setError('');
      await disableTwoFactor(disableCode);
      setTwoFactorEnabled(false);
      setShowDisableModal(false);
      setDisableCode('');
      setSuccess('Đã tắt 2FA thành công');

      // Reload the page to refresh user profile data
      setTimeout(() => {
        window.location.reload();
      }, 1500);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Không thể tắt 2FA');
    }
  };

  const handleRegenerateBackupCodes = async () => {
    if (!regenerateCode || regenerateCode.length !== 6) {
      setError('Vui lòng nhập mã 6 chữ số hợp lệ');
      return;
    }

    try {
      setError('');
      const response = await regenerateBackupCodes(regenerateCode);
      setNewBackupCodes(response.backupCodes);
      setSuccess('Đã tạo lại mã dự phòng thành công');
    } catch (err: any) {
      setError(err.response?.data?.error || 'Không thể tạo lại mã dự phòng');
    }
  };

  const downloadBackupCodes = (codes: string[]) => {
    const text = `Mã Dự Phòng Xác Thực Hai Yếu Tố (2FA)\n\nLưu các mã này ở nơi an toàn.\nMỗi mã chỉ có thể sử dụng một lần.\n\n${codes.join('\n')}`;
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'ma-du-phong-2fa.txt';
    a.click();
    URL.revokeObjectURL(url);
  };

  const closeSetupModal = () => {
    setShowSetupModal(false);
    setSetupData(null);
    setVerificationCode('');
    setSetupStep('qr');
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900 py-8">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-8">
          <Link
            href="/profile"
            className="inline-flex items-center text-sm text-gray-600 hover:text-gray-900 mb-4"
          >
            <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
            Quay lại hồ sơ
          </Link>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white">Cài đặt bảo mật</h1>
          <p className="mt-2 text-gray-600 dark:text-gray-400">
            Quản lý bảo mật tài khoản và xác thực hai yếu tố
          </p>
        </div>

        {/* Alert Messages */}
        {error && (
          <div className="mb-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
            <p className="text-red-800 dark:text-red-200">{error}</p>
          </div>
        )}

        {success && (
          <div className="mb-6 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4">
            <p className="text-green-800 dark:text-green-200">{success}</p>
          </div>
        )}

        {/* Two-Factor Authentication Card */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <div className="flex items-center gap-3 mb-4">
                <div className="p-3 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
                  <svg className="w-6 h-6 text-blue-600 dark:text-blue-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                  </svg>
                </div>
                <div>
                  <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
                    Xác thực hai yếu tố (2FA)
                  </h2>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                    Thêm lớp bảo mật bổ sung cho tài khoản của bạn
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3 mb-4">
                <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Trạng thái:</span>
                {twoFactorEnabled ? (
                  <span className="inline-flex items-center gap-2 px-3 py-1 bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 rounded-full text-sm font-medium">
                    <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                    </svg>
                    Đã bật
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-2 px-3 py-1 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-full text-sm font-medium">
                    <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                    </svg>
                    Chưa bật
                  </span>
                )}
              </div>

              <p className="text-gray-600 dark:text-gray-400 text-sm">
                Xác thực hai yếu tố thêm một lớp bảo mật bổ sung cho tài khoản của bạn bằng cách yêu cầu nhiều hơn chỉ mật khẩu để đăng nhập.
              </p>
            </div>
          </div>

          <div className="mt-6 flex gap-3">
            {!twoFactorEnabled ? (
              <button
                onClick={handleEnableTwoFactor}
                className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors"
              >
                Bật 2FA
              </button>
            ) : (
              <>
                <button
                  onClick={() => setShowDisableModal(true)}
                  className="px-6 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-lg font-medium transition-colors"
                >
                  Tắt 2FA
                </button>
                <button
                  onClick={() => setShowRegenerateModal(true)}
                  className="px-6 py-2.5 bg-gray-600 hover:bg-gray-700 text-white rounded-lg font-medium transition-colors"
                >
                  Tạo lại mã dự phòng
                </button>
              </>
            )}
          </div>
        </div>

        {/* Setup Modal */}
        {showSetupModal && setupData && (
          <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
            <div className="bg-white dark:bg-gray-800 rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto">
              <div className="p-6">
                <div className="flex items-center justify-between mb-6">
                  <h3 className="text-2xl font-bold text-gray-900 dark:text-white">
                    {setupStep === 'qr' && 'Quét mã QR'}
                    {setupStep === 'verify' && 'Xác minh mã'}
                    {setupStep === 'backup' && 'Lưu mã dự phòng'}
                  </h3>
                  <button
                    onClick={closeSetupModal}
                    className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                  >
                    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>

                {/* Step 1: QR Code */}
                {setupStep === 'qr' && (
                  <div className="space-y-6">
                    <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
                      <p className="text-blue-800 dark:text-blue-200 text-sm">
                        <strong>Bước 1:</strong> Quét mã QR này bằng ứng dụng xác thực của bạn (Google Authenticator, Authy, v.v.)
                      </p>
                    </div>

                    <div className="flex justify-center">
                      <div className="bg-white p-4 rounded-lg shadow-md">
                        <img src={setupData.qrCodeDataUrl} alt="Mã QR 2FA" className="w-64 h-64" />
                      </div>
                    </div>

                    <div>
                      <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">Hoặc nhập mã này thủ công:</p>
                      <div className="bg-gray-100 dark:bg-gray-700 p-3 rounded-lg">
                        <code className="text-sm font-mono text-gray-900 dark:text-white break-all">{setupData.secret}</code>
                      </div>
                    </div>

                    <button
                      onClick={() => setSetupStep('verify')}
                      className="w-full px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors"
                    >
                      Tiếp theo: Xác minh mã
                    </button>
                  </div>
                )}

                {/* Step 2: Verify Code */}
                {setupStep === 'verify' && (
                  <div className="space-y-6">
                    <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
                      <p className="text-blue-800 dark:text-blue-200 text-sm">
                        <strong>Bước 2:</strong> Nhập mã 6 chữ số từ ứng dụng xác thực của bạn để xác minh
                      </p>
                    </div>

                    {error && (
                      <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                        <p className="text-red-800 dark:text-red-200 text-sm">{error}</p>
                      </div>
                    )}

                    <div>
                      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                        Mã xác minh
                      </label>
                      <input
                        type="text"
                        value={verificationCode}
                        onChange={(e) => setVerificationCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                        placeholder="000000"
                        className="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent dark:bg-gray-700 dark:text-white text-center text-2xl font-mono tracking-widest"
                        maxLength={6}
                      />
                    </div>

                    <div className="flex gap-3">
                      <button
                        onClick={() => setSetupStep('qr')}
                        className="flex-1 px-6 py-3 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg font-medium hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                      >
                        Quay lại
                      </button>
                      <button
                        onClick={handleVerifyCode}
                        disabled={verificationCode.length !== 6}
                        className="flex-1 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        Xác minh & Bật
                      </button>
                    </div>
                  </div>
                )}

                {/* Step 3: Backup Codes */}
                {setupStep === 'backup' && (
                  <div className="space-y-6">
                    <div className="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4">
                      <p className="text-green-800 dark:text-green-200 text-sm">
                        <strong>Thành công!</strong> 2FA đã được bật trên tài khoản của bạn.
                      </p>
                    </div>

                    <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4">
                      <p className="text-yellow-800 dark:text-yellow-200 text-sm">
                        <strong>Quan trọng:</strong> Lưu các mã dự phòng này ở nơi an toàn. Mỗi mã chỉ có thể được sử dụng một lần.
                      </p>
                    </div>

                    <div className="bg-gray-100 dark:bg-gray-700 rounded-lg p-4">
                      <div className="grid grid-cols-2 gap-3">
                        {setupData.backupCodes.map((code, index) => (
                          <div key={index} className="bg-white dark:bg-gray-800 p-3 rounded font-mono text-center text-sm">
                            {code}
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="flex gap-3">
                      <button
                        onClick={() => downloadBackupCodes(setupData.backupCodes)}
                        className="flex-1 px-6 py-3 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg font-medium hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                      >
                        Tải xuống mã
                      </button>
                      <button
                        onClick={closeSetupModal}
                        className="flex-1 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors"
                      >
                        Hoàn tất
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* Disable Modal */}
        {showDisableModal && (
          <div className="fixed inset-0 bg-opacity-50 flex items-center justify-center z-50 p-4">
            <div className="bg-white dark:bg-gray-800 rounded-lg max-w-md w-full p-6">
              <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4">Tắt xác thực hai yếu tố</h3>

              {error && (
                <div className="mb-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                  <p className="text-red-800 dark:text-red-200 text-sm">{error}</p>
                </div>
              )}

              <p className="text-gray-600 dark:text-gray-400 mb-4">
                Nhập mã xác thực 6 chữ số của bạn để tắt 2FA.
              </p>

              <div className="mb-6">
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                  Mã xác minh
                </label>
                <input
                  type="text"
                  value={disableCode}
                  onChange={(e) => setDisableCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  className="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent dark:bg-gray-700 dark:text-white text-center text-2xl font-mono tracking-widest"
                  maxLength={6}
                />
              </div>

              <div className="flex gap-3">
                <button
                  onClick={() => {
                    setShowDisableModal(false);
                    setDisableCode('');
                    setError('');
                  }}
                  className="flex-1 px-6 py-3 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg font-medium hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                >
                  Hủy
                </button>
                <button
                  onClick={handleDisableTwoFactor}
                  disabled={disableCode.length !== 6}
                  className="flex-1 px-6 py-3 bg-red-600 hover:bg-red-700 text-white rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Tắt
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Regenerate Backup Codes Modal */}
        {showRegenerateModal && (
          <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
            <div className="bg-white dark:bg-gray-800 rounded-lg max-w-md w-full p-6">
              <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4">Tạo lại mã dự phòng</h3>

              {error && (
                <div className="mb-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                  <p className="text-red-800 dark:text-red-200 text-sm">{error}</p>
                </div>
              )}

              {newBackupCodes.length > 0 ? (
                <div className="space-y-4">
                  <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4">
                    <p className="text-yellow-800 dark:text-yellow-200 text-sm">
                      <strong>Quan trọng:</strong> Các mã dự phòng cũ của bạn hiện đã không hợp lệ. Hãy lưu các mã mới này ở nơi an toàn.
                    </p>
                  </div>

                  <div className="bg-gray-100 dark:bg-gray-700 rounded-lg p-4">
                    <div className="grid grid-cols-2 gap-3">
                      {newBackupCodes.map((code, index) => (
                        <div key={index} className="bg-white dark:bg-gray-800 p-3 rounded font-mono text-center text-sm">
                          {code}
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="flex gap-3">
                    <button
                      onClick={() => downloadBackupCodes(newBackupCodes)}
                      className="flex-1 px-6 py-3 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg font-medium hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                    >
                      Tải xuống mã
                    </button>
                    <button
                      onClick={() => {
                        setShowRegenerateModal(false);
                        setRegenerateCode('');
                        setNewBackupCodes([]);
                        setError('');
                      }}
                      className="flex-1 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors"
                    >
                      Hoàn tất
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  <p className="text-gray-600 dark:text-gray-400 mb-4">
                    Nhập mã xác thực 6 chữ số của bạn để tạo mã dự phòng mới.
                  </p>

                  <div className="mb-6">
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                      Mã xác minh
                    </label>
                    <input
                      type="text"
                      value={regenerateCode}
                      onChange={(e) => setRegenerateCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                      placeholder="000000"
                      className="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent dark:bg-gray-700 dark:text-white text-center text-2xl font-mono tracking-widest"
                      maxLength={6}
                    />
                  </div>

                  <div className="flex gap-3">
                    <button
                      onClick={() => {
                        setShowRegenerateModal(false);
                        setRegenerateCode('');
                        setError('');
                      }}
                      className="flex-1 px-6 py-3 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg font-medium hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                    >
                      Hủy
                    </button>
                    <button
                      onClick={handleRegenerateBackupCodes}
                      disabled={regenerateCode.length !== 6}
                      className="flex-1 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      Tạo lại
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
