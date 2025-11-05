/**
 * Checkout Page
 * Multi-step checkout with address selection, coupon, and payment
 * Requires authentication
 */

'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/contexts/AuthContext';
import { getCart } from '@/lib/api/cart';
import { getUserAddresses, createAddress } from '@/lib/api/addresses';
import { validateCoupon } from '@/lib/api/coupons';
import { checkout } from '@/lib/api/orders';
import { createPayment } from '@/lib/api/payments';
import type { CreateAddressRequest } from '@/types/address';
import type { CheckoutRequest } from '@/types/order';

export default function CheckoutPage() {
  const router = useRouter();
  const { isAuthenticated, user, loading: authLoading } = useAuth();
  const queryClient = useQueryClient();

  // State
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [couponCode, setCouponCode] = useState('');
  const [appliedCoupon, setAppliedCoupon] = useState<any>(null);
  const [email, setEmail] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);

  // New address form state
  const [newAddress, setNewAddress] = useState<CreateAddressRequest>({
    addressLine1: '',
    addressLine2: '',
    city: '',
    stateProvince: '',
    postalCode: '',
    country: 'Vietnam',
    phoneNumber: '',
    type: 'SHIPPING',
    isDefault: false,
  });

  // Redirect if not authenticated
  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.push('/login?redirect=/checkout');
    }
  }, [isAuthenticated, authLoading, router]);

  // Set email from user
  useEffect(() => {
    if (user?.email) {
      setEmail(user.email);
    }
  }, [user]);

  // Fetch cart
  const { data: cart, isLoading: cartLoading } = useQuery({
    queryKey: ['cart'],
    queryFn: getCart,
    enabled: isAuthenticated,
  });

  // Fetch addresses
  const { data: addressesResponse, isLoading: addressesLoading } = useQuery({
    queryKey: ['addresses'],
    queryFn: getUserAddresses,
    enabled: isAuthenticated,
  });

  const addresses = addressesResponse?.addresses || [];

  // Auto-select default address
  useEffect(() => {
    if (addresses.length > 0 && !selectedAddressId) {
      const defaultAddr = addresses.find((addr) => addr.isDefault);
      if (defaultAddr) {
        setSelectedAddressId(defaultAddr.id);
      } else {
        setSelectedAddressId(addresses[0].id);
      }
    }
  }, [addresses, selectedAddressId]);

  // Create address mutation
  const createAddressMutation = useMutation({
    mutationFn: createAddress,
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['addresses'] });
      setSelectedAddressId(response.address.id);
      setShowAddressForm(false);
      // Reset form
      setNewAddress({
        addressLine1: '',
        addressLine2: '',
        city: '',
        stateProvince: '',
        postalCode: '',
        country: 'Vietnam',
        phoneNumber: '',
        type: 'SHIPPING',
        isDefault: false,
      });
    },
    onError: (error: any) => {
      alert(`Failed to create address: ${error.message}`);
    },
  });

  // Validate coupon
  const validateCouponMutation = useMutation({
    mutationFn: () => validateCoupon(couponCode, cart?.totalPrice || 0),
    onSuccess: (response) => {
      if (response.valid) {
        setAppliedCoupon(response);
        alert(`Coupon applied! Discount: ${response.discountAmount?.toLocaleString('vi-VN')} ₫`);
      } else {
        setAppliedCoupon(null);
        alert(response.message);
      }
    },
    onError: (error: any) => {
      alert(`Failed to validate coupon: ${error.message}`);
      setAppliedCoupon(null);
    },
  });

  // Calculate totals
  const subtotal = cart?.totalPrice || 0;
  const shippingCost = subtotal >= 100 ? 0 : 10;
  const taxAmount = subtotal * 0.1;
  const discountAmount = appliedCoupon?.discountAmount || 0;
  const freeShipping = appliedCoupon?.freeShipping || false;
  const finalShipping = freeShipping ? 0 : shippingCost;
  const total = subtotal + finalShipping + taxAmount - discountAmount;

  // Handle checkout
  const handleCheckout = async () => {
    if (!selectedAddressId) {
      alert('Please select a shipping address');
      return;
    }

    if (!email) {
      alert('Please provide your email address');
      return;
    }

    const selectedAddress = addresses.find((addr) => addr.id === selectedAddressId);
    if (!selectedAddress) {
      alert('Selected address not found');
      return;
    }

    setIsProcessing(true);

    try {
      // Step 1: Create order
      const checkoutRequest: CheckoutRequest = {
        shippingAddress: {
          addressLine1: selectedAddress.addressLine1,
          addressLine2: selectedAddress.addressLine2,
          city: selectedAddress.city,
          stateProvince: selectedAddress.stateProvince,
          postalCode: selectedAddress.postalCode,
          country: selectedAddress.country,
          phoneNumber: selectedAddress.phoneNumber,
        },
        email,
        couponCode: appliedCoupon?.code,
        paymentMethod: 'PAYPAL',
      };

      const checkoutResponse = await checkout(checkoutRequest);
      console.log('[Checkout] Order created:', checkoutResponse.order.orderNumber);

      // Step 2: Create payment
      const paymentResponse = await createPayment({ orderId: checkoutResponse.order.id });
      console.log('[Checkout] Payment created:', paymentResponse.paypalOrderId);

      // Step 3: Redirect to PayPal
      window.location.href = paymentResponse.approvalUrl;
    } catch (error: any) {
      console.error('[Checkout] Failed:', error);
      alert(`Checkout failed: ${error.message || 'Unknown error'}`);
      setIsProcessing(false);
    }
  };

  if (authLoading || cartLoading || addressesLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  if (!cart || cart.items.length === 0) {
    return (
      <div className="min-h-screen bg-gray-50 py-12">
        <div className="max-w-3xl mx-auto px-4 text-center">
          <h1 className="text-2xl font-bold mb-4">Giỏ hàng của bạn đang trống</h1>
          <button
            onClick={() => router.push('/products')}
            className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            Tiếp Tục Mua Sắm
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-8">Thanh Toán</h1>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Left Column - Address & Contact */}
          <div className="lg:col-span-2 space-y-6">
            {/* Shipping Address Section */}
            <div className="bg-white rounded-lg shadow-sm p-6">
              <h2 className="text-xl font-semibold mb-4">Địa Chỉ Giao Hàng</h2>

              {addresses.length > 0 && !showAddressForm && (
                <div className="space-y-3 mb-4">
                  {addresses.map((addr) => (
                    <div
                      key={addr.id}
                      onClick={() => setSelectedAddressId(addr.id)}
                      className={`p-4 border-2 rounded-lg cursor-pointer transition-colors ${
                        selectedAddressId === addr.id
                          ? 'border-blue-600 bg-blue-50'
                          : 'border-gray-200 hover:border-gray-300'
                      }`}
                    >
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-2">
                            <input
                              type="radio"
                              checked={selectedAddressId === addr.id}
                              onChange={() => setSelectedAddressId(addr.id)}
                              className="w-4 h-4 text-blue-600"
                            />
                            {addr.isDefault && (
                              <span className="text-xs bg-blue-100 text-blue-800 px-2 py-1 rounded">
                                Mặc Định
                              </span>
                            )}
                          </div>
                          <p className="font-medium">{addr.addressLine1}</p>
                          {addr.addressLine2 && <p className="text-gray-600">{addr.addressLine2}</p>}
                          <p className="text-gray-600">
                            {addr.city}, {addr.stateProvince} {addr.postalCode}
                          </p>
                          <p className="text-gray-600">{addr.country}</p>
                          {addr.phoneNumber && <p className="text-gray-600">{addr.phoneNumber}</p>}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Add New Address Button */}
              {!showAddressForm && (
                <button
                  onClick={() => setShowAddressForm(true)}
                  className="w-full py-3 border-2 border-dashed border-gray-300 rounded-lg text-gray-600 hover:border-blue-500 hover:text-blue-600 transition-colors"
                >
                  + Thêm Địa Chỉ Mới
                </button>
              )}

              {/* New Address Form */}
              {showAddressForm && (
                <div className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium mb-1">Địa Chỉ Dòng 1 *</label>
                      <input
                        type="text"
                        value={newAddress.addressLine1}
                        onChange={(e) => setNewAddress({ ...newAddress, addressLine1: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium mb-1">Địa Chỉ Dòng 2</label>
                      <input
                        type="text"
                        value={newAddress.addressLine2}
                        onChange={(e) => setNewAddress({ ...newAddress, addressLine2: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium mb-1">Thành Phố *</label>
                      <input
                        type="text"
                        value={newAddress.city}
                        onChange={(e) => setNewAddress({ ...newAddress, city: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium mb-1">Tỉnh/Thành *</label>
                      <input
                        type="text"
                        value={newAddress.stateProvince}
                        onChange={(e) => setNewAddress({ ...newAddress, stateProvince: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium mb-1">Mã Bưu Điện *</label>
                      <input
                        type="text"
                        value={newAddress.postalCode}
                        onChange={(e) => setNewAddress({ ...newAddress, postalCode: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium mb-1">Quốc Gia *</label>
                      <input
                        type="text"
                        value={newAddress.country}
                        onChange={(e) => setNewAddress({ ...newAddress, country: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg"
                        required
                      />
                    </div>
                    <div className="md:col-span-2">
                      <label className="block text-sm font-medium mb-1">Số Điện Thoại</label>
                      <input
                        type="tel"
                        value={newAddress.phoneNumber}
                        onChange={(e) => setNewAddress({ ...newAddress, phoneNumber: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg"
                      />
                    </div>
                  </div>

                  <div className="flex gap-3">
                    <button
                      onClick={() => createAddressMutation.mutate(newAddress)}
                      disabled={
                        !newAddress.addressLine1 ||
                        !newAddress.city ||
                        !newAddress.stateProvince ||
                        !newAddress.postalCode ||
                        !newAddress.country ||
                        createAddressMutation.isPending
                      }
                      className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
                    >
                      {createAddressMutation.isPending ? 'Đang Lưu...' : 'Lưu Địa Chỉ'}
                    </button>
                    <button
                      onClick={() => setShowAddressForm(false)}
                      className="px-6 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
                    >
                      Hủy
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* Contact Information */}
            <div className="bg-white rounded-lg shadow-sm p-6">
              <h2 className="text-xl font-semibold mb-4">Thông Tin Liên Hệ</h2>
              <div>
                <label className="block text-sm font-medium mb-1">Địa Chỉ Email *</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg"
                  placeholder="email@example.com"
                  required
                />
                <p className="text-sm text-gray-500 mt-1">Xác nhận đơn hàng sẽ được gửi đến email này</p>
              </div>
            </div>
          </div>

          {/* Right Column - Order Summary */}
          <div>
            <div className="bg-white rounded-lg shadow-sm p-6 sticky top-4">
              <h2 className="text-xl font-semibold mb-4">Tóm Tắt Đơn Hàng</h2>

              {/* Cart Items */}
              <div className="space-y-3 mb-4 max-h-64 overflow-y-auto">
                {cart.items.map((item) => (
                  <div key={item.id} className="flex gap-3">
                    <img
                      src={item.productImage || 'https://placehold.co/60x60?text=No+Image'}
                      alt={item.productName}
                      className="w-16 h-16 object-cover rounded"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="font-medium text-sm truncate">{item.productName}</p>
                      <p className="text-sm text-gray-600">
                        {item.quantity} x {item.priceAtAdd.toLocaleString('vi-VN')} ₫
                      </p>
                      {item.size && <p className="text-xs text-gray-500">Size: {item.size}</p>}
                      {item.color && <p className="text-xs text-gray-500">Color: {item.color}</p>}
                    </div>
                  </div>
                ))}
              </div>

              {/* Coupon Section */}
              <div className="mb-4 pb-4 border-b">
                <label className="block text-sm font-medium mb-2">Mã Giảm Giá</label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={couponCode}
                    onChange={(e) => setCouponCode(e.target.value.toUpperCase())}
                    placeholder="Nhập mã"
                    className="flex-1 px-3 py-2 border rounded-lg text-sm"
                  />
                  <button
                    onClick={() => validateCouponMutation.mutate()}
                    disabled={!couponCode || validateCouponMutation.isPending}
                    className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 disabled:opacity-50 text-sm"
                  >
                    {validateCouponMutation.isPending ? 'Đang Kiểm Tra...' : 'Áp Dụng'}
                  </button>
                </div>
                {appliedCoupon && (
                  <div className="mt-2 p-2 bg-green-50 border border-green-200 rounded text-sm text-green-800">
                    ✓ {appliedCoupon.description}
                  </div>
                )}
              </div>

              {/* Price Breakdown */}
              <div className="space-y-2 mb-4">
                <div className="flex justify-between text-sm">
                  <span className="text-gray-600">Tổng Phụ</span>
                  <span>{subtotal.toLocaleString('vi-VN')} ₫</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-gray-600">Vận Chuyển</span>
                  <span>
                    {freeShipping ? (
                      <span className="text-green-600">MIỄN PHÍ</span>
                    ) : finalShipping === 0 ? (
                      <span className="text-green-600">MIỄN PHÍ (Đơn ≥ 1.000.000 đ)</span>
                    ) : (
                      `${finalShipping.toLocaleString('vi-VN')} ₫`
                    )}
                  </span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-gray-600">Thuế (10%)</span>
                  <span>{taxAmount.toLocaleString('vi-VN')} ₫</span>
                </div>
                {discountAmount > 0 && (
                  <div className="flex justify-between text-sm text-green-600">
                    <span>Giảm Giá</span>
                    <span>-{discountAmount.toLocaleString('vi-VN')} ₫</span>
                  </div>
                )}
                <div className="border-t pt-2 flex justify-between font-bold text-lg">
                  <span>Tổng Cộng</span>
                  <span className="text-blue-600">{total.toLocaleString('vi-VN')} ₫</span>
                </div>
              </div>

              {/* Place Order Button */}
              <button
                onClick={handleCheckout}
                disabled={isProcessing || !selectedAddressId || !email}
                className="w-full py-3 bg-blue-600 text-white rounded-lg font-semibold hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isProcessing ? 'Đang Xử Lý...' : 'Đặt Hàng & Thanh Toán Với PayPal'}
              </button>

              <p className="text-xs text-gray-500 text-center mt-3">
                Bạn sẽ được chuyển đến PayPal để hoàn tất thanh toán
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
