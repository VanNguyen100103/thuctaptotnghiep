/**
 * Product Section Component
 * Section with icon, title, and "Xem Tất Cả" link (Coolmate style)
 */

'use client';

import Link from 'next/link';
import ProductGrid from './ProductGrid';
import type { Product } from '@/types/product';

interface ProductSectionProps {
  title: string;
  icon?: string; // emoji or icon
  products: Product[];
  viewAllLink?: string;
  badge?: 'BEST SELLER' | 'MỚI' | 'SALE' | 'NỔI BẬT' | null;
  columns?: 2 | 3 | 4 | 5;
}

export default function ProductSection({
  title,
  icon,
  products,
  viewAllLink,
  badge = null,
  columns = 4,
}: ProductSectionProps) {
  return (
    <section className="mb-12">
      {/* Section Header */}
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl md:text-3xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
          {icon && <span className="text-3xl">{icon}</span>}
          {title}
        </h2>
        {viewAllLink && (
          <Link
            href={viewAllLink}
            className="text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 font-medium transition-colors flex items-center gap-1"
          >
            Xem Tất Cả
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        )}
      </div>

      {/* Product Grid */}
      <ProductGrid products={products} columns={columns} badge={badge} />
    </section>
  );
}
