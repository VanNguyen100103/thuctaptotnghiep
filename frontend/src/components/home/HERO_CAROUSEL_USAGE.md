# Hero Carousel & Header Components (Coolmate Design)

Components giống Coolmate.me với auto-sliding banners và header

## Components

### 1. HeroCarousel
Auto-sliding full-width banner carousel với navigation

**Usage:**
```tsx
import { HeroCarousel } from '@/components/home';

const banners = [
  {
    id: '1',
    title: 'FALL WINTER',
    subtitle: 'Coolmate X Thanh Sơn X Nhung Trần',
    ctaText: 'MUA NGAY',
    ctaLink: '/shop/fall-winter',
    imageUrl: '/images/banners/fall-winter.jpg',
    badge: 'Bật chế độ',
    badgeActive: true,
    backgroundColor: '#7B7B7B',
    textColor: '#FFFFFF'
  },
  {
    id: '2',
    title: 'SMALL BUT MIGHTY',
    subtitle: '100 đơn đầu tiên nhận thiệp có chữ ký Nguyễn Thị Oanh',
    ctaText: 'MUA NGAY',
    ctaLink: '/collections/small-but-mighty',
    imageUrl: '/images/banners/athlete.jpg',
    backgroundColor: '#A84A4A',
    textColor: '#FFFFFF'
  },
  {
    id: '3',
    title: 'TEAMWEAR COLLECTION',
    subtitle: 'Nhập "ACTIVEDRY" tặng khăn tập cho đơn từ 499k',
    ctaText: 'MUA NGAY',
    ctaLink: '/collections/teamwear',
    imageUrl: '/images/banners/teamwear.jpg',
    backgroundColor: '#FFFFFF',
    textColor: '#000000'
  }
];

<HeroCarousel banners={banners} autoPlayInterval={5000} />
```

### 2. Header
Sticky header với search, cart, user menu

**Usage:**
```tsx
import { Header } from '@/components/layout';

<Header
  announcementBar={{
    text: 'NHẬP CMFW25 GIẢM 50K CHO ĐƠN ĐỒ THU ĐÔNG TỪ 499K',
    link: '/promo'
  }}
/>
```

## Full Homepage Example

```tsx
'use client';

import { Header } from '@/components/layout';
import { HeroCarousel } from '@/components/home';
import { ProductSection } from '@/components/product';

export default function HomePage() {
  const banners = [
    {
      id: '1',
      title: 'FALL WINTER',
      subtitle: 'Coolmate X Thanh Sơn X Nhung Trần',
      ctaText: 'MUA NGAY',
      ctaLink: '/shop',
      imageUrl: '/banners/fall-winter.jpg',
      backgroundColor: '#7B7B7B',
      textColor: '#FFFFFF'
    }
  ];

  return (
    <div>
      {/* Header */}
      <Header
        announcementBar={{
          text: 'MIỄN PHÍ VẬN CHUYỂN CHO ĐƠN HÀNG TRÊN 499K'
        }}
      />

      {/* Hero Carousel */}
      <HeroCarousel banners={banners} />

      {/* Product Sections */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <ProductSection
          title="Bán Chạy Nhất"
          icon="🔥"
          products={bestSellers}
          viewAllLink="/shop?bestsellers=true"
          badge="BEST SELLER"
        />
      </div>
    </div>
  );
}
```

## Features

### HeroCarousel:
✅ Auto-slide với custom interval
✅ Pause on hover
✅ Navigation arrows
✅ Dots indicator
✅ Badge toggle (Sống Động/Tắt)
✅ Responsive (mobile shows overlay)
✅ Custom background color per slide
✅ Smooth transitions

### Header:
✅ Sticky position
✅ Announcement bar
✅ Search overlay
✅ User menu dropdown
✅ Cart with count badge
✅ Navigation menu
✅ Responsive design

## Customization

### Banner Colors
Sử dụng màu của Coolmate:
- Gray: `#7B7B7B`
- Red: `#A84A4A`
- White: `#FFFFFF`
- Black: `#000000`

### Animation Speed
```tsx
<HeroCarousel autoPlayInterval={5000} /> // 5 seconds
```

## Images
Đặt banner images trong:
```
public/images/banners/
├── fall-winter.jpg
├── athlete.jpg
├── teamwear.jpg
└── ...
```

Recommended size: 1920x600px
