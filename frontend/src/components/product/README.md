# Product Components (Coolmate Design)

Các components hiển thị sản phẩm theo phong cách Coolmate.me

## Components

### 1. ProductCard
Card hiển thị sản phẩm với:
- ✅ Badge tự động (BEST SELLER / MỚI / SALE)
- ✅ Quick add to cart với size selector (hiện khi hover)
- ✅ Hover effect zoom ảnh
- ✅ Responsive design

**Usage:**
```tsx
import { ProductCard } from '@/components/product';

<ProductCard
  product={product}
  badge="BEST SELLER" // optional, auto-detect nếu không có
/>
```

### 2. ProductGrid
Grid responsive hiển thị nhiều products

**Usage:**
```tsx
import { ProductGrid } from '@/components/product';

<ProductGrid
  products={products}
  columns={4} // 2 | 3 | 4 | 5
  badge="MỚI" // optional
/>
```

### 3. ProductSection
Section với heading và "Xem Tất Cả" link

**Usage:**
```tsx
import { ProductSection } from '@/components/product';

<ProductSection
  title="Bán Chạy Nhất"
  icon="🔥"
  products={products}
  viewAllLink="/shop/best-sellers"
  badge="BEST SELLER"
  columns={4}
/>
```

## Example: Homepage với nhiều sections

```tsx
'use client';

import { ProductSection } from '@/components/product';

export default function HomePage({
  bestSellers,
  newArrivals
}: {
  bestSellers: Product[],
  newArrivals: Product[]
}) {
  return (
    <div className="container mx-auto px-4 py-8">
      {/* Best Sellers */}
      <ProductSection
        title="Bán Chạy Nhất"
        icon="🔥"
        products={bestSellers}
        viewAllLink="/shop?sort=best-selling"
        badge="BEST SELLER"
        columns={4}
      />

      {/* New Arrivals */}
      <ProductSection
        title="Hàng Mới Về"
        icon="✨"
        products={newArrivals}
        viewAllLink="/shop?sort=newest"
        badge="MỚI"
        columns={4}
      />
    </div>
  );
}
```

## Badges

Badge tự động detect dựa trên:
- **BEST SELLER**: reviewCount > 100 hoặc averageRating >= 4.5
- **MỚI**: createdAt trong vòng 7 ngày
- **SALE**: có compareAtPrice (discount)

Hoặc bạn có thể set badge thủ công:
```tsx
<ProductCard product={product} badge="BEST SELLER" />
```

## Quick Add to Cart

ProductCard tự động hiển thị size selector khi:
1. User hover vào card
2. Product có `availableSizes`

Khi click vào size → tự động add to cart với size đó.

## Styling

Components sử dụng Tailwind CSS và dark mode support:
- Light mode: bg-white
- Dark mode: bg-gray-800

## Notes

- Cần có `@tanstack/react-query` installed
- Cần có `next/image` configured
- Cart API phải support `size` parameter trong `AddToCartRequest`
