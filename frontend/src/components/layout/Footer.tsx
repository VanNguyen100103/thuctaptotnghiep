/**
 * Footer Component (Coolmate Design)
 * Modern footer with multiple sections and social links
 */

'use client';

import Link from 'next/link';

export default function Footer() {
  return (
    <footer className="bg-black text-white">
      {/* Top Section - CTA */}
      <div className="border-b border-gray-800">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
          <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
            <div className="flex-1">
              <h3 className="text-2xl md:text-3xl font-bold mb-3">
                Cửa Hàng Thời Trang lắng nghe bạn!
              </h3>
              <p className="text-gray-400 text-sm md:text-base max-w-2xl">
                Chúng tôi luôn trân trọng và mong đợi nhận được mọi ý kiến đóng góp từ khách hàng
                để có thể nâng cấp trải nghiệm dịch vụ và sản phẩm tốt hơn nữa.
              </p>
            </div>
            <Link
              href="/feedback"
              className="bg-white text-black px-6 py-3 rounded-full font-semibold hover:bg-gray-100 transition-colors flex items-center gap-2 whitespace-nowrap"
            >
              ĐÓNG GÓP Ý KIẾN
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </Link>
          </div>
        </div>
      </div>

      {/* Main Footer Content */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-8">
          {/* Column 1: COOLCLUB */}
          <div>
            <h4 className="text-lg font-bold mb-4 uppercase">CoolClub</h4>
            <ul className="space-y-3 text-sm text-gray-400">
              <li><Link href="/account" className="hover:text-white transition-colors">Tài khoản CoolClub</Link></li>
              <li><Link href="/register" className="hover:text-white transition-colors">Đăng ký thành viên</Link></li>
              <li><Link href="/benefits" className="hover:text-white transition-colors">Ưu đãi & Đặc quyền</Link></li>
            </ul>

            <h4 className="text-lg font-bold mb-4 mt-8 uppercase">Tài liệu - Tuyển dụng</h4>
            <ul className="space-y-3 text-sm text-gray-400">
              <li><Link href="/careers" className="hover:text-white transition-colors">Tuyển dụng</Link></li>
              <li><Link href="/policy" className="hover:text-white transition-colors">Đăng ký bán quyền</Link></li>
            </ul>
          </div>

          {/* Column 2: Chính sách */}
          <div>
            <h4 className="text-lg font-bold mb-4 uppercase">Chính sách</h4>
            <ul className="space-y-3 text-sm text-gray-400">
              <li><Link href="/return-policy" className="hover:text-white transition-colors">Chính sách đổi trả 60 ngày</Link></li>
              <li><Link href="/promotion-policy" className="hover:text-white transition-colors">Chính sách khuyến mãi</Link></li>
              <li><Link href="/security-policy" className="hover:text-white transition-colors">Chính sách bảo mật</Link></li>
              <li><Link href="/shipping-policy" className="hover:text-white transition-colors">Chính sách giao hàng</Link></li>
            </ul>

            <h4 className="text-lg font-bold mb-4 mt-8 uppercase">Coolmate.me</h4>
            <ul className="space-y-3 text-sm text-gray-400">
              <li><Link href="/about" className="hover:text-white transition-colors">Lịch sử thay đổi website</Link></li>
            </ul>
          </div>

          {/* Column 3: Chăm sóc khách hàng */}
          <div>
            <h4 className="text-lg font-bold mb-4 uppercase">Chăm sóc khách hàng</h4>
            <ul className="space-y-3 text-sm text-gray-400">
              <li><Link href="/guarantee" className="hover:text-white transition-colors">Trải nghiệm mua sắm 100% hài lòng</Link></li>
              <li><Link href="/faq" className="hover:text-white transition-colors">Hỏi đáp - FAQs</Link></li>
            </ul>

            <h4 className="text-lg font-bold mb-4 mt-8 uppercase">Kiến thức mặc đẹp</h4>
            <ul className="space-y-3 text-sm text-gray-400">
              <li><Link href="/size-guide" className="hover:text-white transition-colors">Hướng dẫn chọn size</Link></li>
              <li><Link href="/blog" className="hover:text-white transition-colors">Blog</Link></li>
            </ul>
          </div>

          {/* Column 4: Về Coolmate */}
          <div>
            <h4 className="text-lg font-bold mb-4 uppercase">Về Cửa Hàng</h4>
            <ul className="space-y-3 text-sm text-gray-400">
              <li><Link href="/about" className="hover:text-white transition-colors">Quy tắc ứng xử của Coolmate</Link></li>
              <li><Link href="/coolmate-101" className="hover:text-white transition-colors">Coolmate 101</Link></li>
              <li><Link href="/ip" className="hover:text-white transition-colors">DVKH xuất sắc</Link></li>
              <li><Link href="/story" className="hover:text-white transition-colors">Câu chuyện về Coolmate</Link></li>
              <li><Link href="/factory" className="hover:text-white transition-colors">Nhà máy</Link></li>
              <li><Link href="/care-share" className="hover:text-white transition-colors">Care & Share</Link></li>
              <li><Link href="/partners" className="hover:text-white transition-colors">Cam kết bền vững</Link></li>
              <li><Link href="/vision-2030" className="hover:text-white transition-colors">Tầm nhìn 2030</Link></li>
            </ul>
          </div>

          {/* Column 5: Địa chỉ liên hệ */}
          <div>
            <h4 className="text-lg font-bold mb-4 uppercase">Địa chỉ liên hệ</h4>

            {/* Hanoi */}
            <div className="mb-6">
              <p className="font-semibold text-sm mb-2">Văn phòng Hà Nội:</p>
              <p className="text-sm text-gray-400">
                Tầng 3-4, Tòa nhà BMM, Km2, Đường Phùng Hưng, Phường Hà Đông, Thành phố Hà Nội, Việt Nam
              </p>
            </div>

            {/* HCMC */}
            <div className="mb-6">
              <p className="font-semibold text-sm mb-2">Văn phòng và Trung tâm vận hành TP.HCM:</p>
              <p className="text-sm text-gray-400">
                Lô C8, KCN Lai Yên, Xã Lai Yên, Huyện Hoài Đức, Thành phố Hà Nội
              </p>
            </div>

            {/* Store */}
            <div>
              <p className="font-semibold text-sm mb-2">Trung tâm R&D:</p>
              <p className="text-sm text-gray-400">
                16-01, The Manhattan Vinhomes Grand Park, Long Bình, TP. Thủ Đức
              </p>
            </div>
          </div>
        </div>

        {/* Contact Info */}
        <div className="mt-12 pt-8 border-t border-gray-800">
          <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
            {/* Contact */}
            <div className="flex flex-col sm:flex-row gap-6">
              <div>
                <p className="text-sm text-gray-400 mb-1">Hotline</p>
                <a href="tel:1900272737" className="text-lg font-semibold hover:text-gray-300 transition-colors">
                  1900.272737 - 028.7777.2737
                </a>
              </div>
              <div>
                <p className="text-sm text-gray-400 mb-1">Email</p>
                <a href="mailto:cool@coolmate.me" className="text-lg font-semibold hover:text-gray-300 transition-colors">
                  cool@coolmate.me
                </a>
              </div>
            </div>

            {/* Social Links */}
            <div className="flex gap-4">
              <a href="https://facebook.com" target="_blank" rel="noopener noreferrer" className="w-10 h-10 border-2 border-white rounded-lg flex items-center justify-center hover:bg-white hover:text-black transition-colors">
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
                </svg>
              </a>
              <a href="https://zalo.me" target="_blank" rel="noopener noreferrer" className="w-10 h-10 border-2 border-white rounded-lg flex items-center justify-center hover:bg-white hover:text-black transition-colors">
                <span className="font-bold text-sm">Z</span>
              </a>
              <a href="https://tiktok.com" target="_blank" rel="noopener noreferrer" className="w-10 h-10 border-2 border-white rounded-lg flex items-center justify-center hover:bg-white hover:text-black transition-colors">
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M19.59 6.69a4.83 4.83 0 0 1-3.77-4.25V2h-3.45v13.67a2.89 2.89 0 0 1-5.2 1.74 2.89 2.89 0 0 1 2.31-4.64 2.93 2.93 0 0 1 .88.13V9.4a6.84 6.84 0 0 0-1-.05A6.33 6.33 0 0 0 5 20.1a6.34 6.34 0 0 0 10.86-4.43v-7a8.16 8.16 0 0 0 4.77 1.52v-3.4a4.85 4.85 0 0 1-1-.1z"/>
                </svg>
              </a>
              <a href="https://instagram.com" target="_blank" rel="noopener noreferrer" className="w-10 h-10 border-2 border-white rounded-lg flex items-center justify-center hover:bg-white hover:text-black transition-colors">
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838c-3.403 0-6.162 2.759-6.162 6.162s2.759 6.163 6.162 6.163 6.162-2.759 6.162-6.163c0-3.403-2.759-6.162-6.162-6.162zm0 10.162c-2.209 0-4-1.79-4-4 0-2.209 1.791-4 4-4s4 1.791 4 4c0 2.21-1.791 4-4 4zm6.406-11.845c-.796 0-1.441.645-1.441 1.44s.645 1.44 1.441 1.44c.795 0 1.439-.645 1.439-1.44s-.644-1.44-1.439-1.44z"/>
                </svg>
              </a>
              <a href="https://youtube.com" target="_blank" rel="noopener noreferrer" className="w-10 h-10 border-2 border-white rounded-lg flex items-center justify-center hover:bg-white hover:text-black transition-colors">
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z"/>
                </svg>
              </a>
            </div>
          </div>
        </div>

        {/* Copyright */}
        <div className="mt-8 pt-8 border-t border-gray-800 text-center text-sm text-gray-400">
          <p>&copy; 2025 Cửa Hàng Thời Trang. Được hỗ trợ bởi Gemini AI.</p>
        </div>
      </div>
    </footer>
  );
}
