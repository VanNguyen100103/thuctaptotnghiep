import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/contexts/AuthContext";
import { QueryProvider } from "@/lib/providers/query-provider";
import FloatingButtons from "@/components/layout/FloatingButtons";
import { Header, Footer } from "@/components/layout";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "E-commerce Fashion Store - Điểm Đến Mua Sắm Của Bạn",
  description: "Khám phá xu hướng thời trang mới nhất với gợi ý từ AI. Quần áo chất lượng, phụ kiện và nhiều hơn nữa.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        <QueryProvider>
          <AuthProvider>
            {/* Header cho toàn bộ ứng dụng */}
            <Header
              announcementBar={{
                text: 'NHẬP CMFW25 GIẢM 50K CHO ĐƠN ĐỒ THU ĐÔNG TỪ 499K',
                link: '/promo'
              }}
            />

            {/* Nội dung chính */}
            {children}

            {/* Footer cho toàn bộ ứng dụng */}
            <Footer />

            {/* Floating buttons */}
            <FloatingButtons />
          </AuthProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
