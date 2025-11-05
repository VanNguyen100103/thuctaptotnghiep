# Next.js Page Architecture & Rendering Strategies

## Overview

This document outlines the complete frontend architecture for the e-commerce application, mapping all backend endpoints to Next.js pages with appropriate rendering strategies (CSR/SSR/ISR/SSG).

## Rendering Strategy Decision Matrix

| Strategy | Use Case | Characteristics | Examples |
|----------|----------|-----------------|----------|
| **SSG** | Static content that rarely changes | Pre-rendered at build time | About, Terms, Privacy |
| **ISR** | Content that changes periodically | Regenerate on schedule or on-demand | Product listings, Categories |
| **SSR** | SEO-critical + real-time data | Rendered on each request | Product detail pages |
| **CSR** | Authenticated/dynamic user data | Client-side rendering only | Cart, Profile, Orders |

---

## Page Structure

### 1. Public Pages (SEO-Critical)

#### 1.1 Homepage `/`
- **Strategy**: ISR (revalidate: 3600 = 1 hour)
- **Data Sources**:
  - Featured products: `getFeaturedProducts()`
  - New arrivals: `getNewArrivals()`
  - Top rated: `getTopRatedProducts()`
  - Trending (AI): `getTrendingProducts()`
  - Categories: `getRootCategories()`
- **Why ISR**: Homepage needs fresh content but doesn't change every second

```typescript
// app/page.tsx
export const revalidate = 3600; // 1 hour

export default async function HomePage() {
  const [featured, newArrivals, trending, categories] = await Promise.all([
    getFeaturedProducts(0, 8),
    getNewArrivals(0, 8),
    getTrendingProducts(10),
    getRootCategories()
  ]);

  return <HomePageClient data={{featured, newArrivals, trending, categories}} />;
}
```

---

#### 1.2 Product Listing `/shop`
- **Strategy**: ISR (revalidate: 1800 = 30 minutes)
- **Data Sources**:
  - Products: `getAllActiveProducts()`
  - Categories for filters: `getAllActiveCategories()`
- **Features**:
  - Pagination
  - Filters (category, price, brand)
  - Search
  - Sort options
- **Why ISR**: Product availability changes, but not real-time critical

```typescript
// app/shop/page.tsx
export const revalidate = 1800; // 30 minutes

export default async function ShopPage({ searchParams }) {
  const products = await getAllActiveProducts({
    page: searchParams.page || 0,
    size: 20,
    categoryId: searchParams.category,
    minPrice: searchParams.minPrice,
    maxPrice: searchParams.maxPrice,
    search: searchParams.search,
    sort: searchParams.sort || 'createdAt,desc'
  });

  const categories = await getAllActiveCategories();

  return <ShopPageClient products={products} categories={categories} />;
}
```

---

#### 1.3 Product Detail `/shop/[slug]`
- **Strategy**: ISR (revalidate: on-demand + 300 = 5 minutes)
- **Data Sources**:
  - Product: `getProductBySlug(slug)`
  - Reviews: `getProductReviews(productId)`
  - Related: `getRelatedProducts(productId)`
  - Similar (AI): `getSimilarProducts(productId)`
  - AI recommendations: `getProductBasedRecommendations(productId)`
- **Why ISR**: SEO-critical + stock changes + reviews added frequently

```typescript
// app/shop/[slug]/page.tsx
export const revalidate = 300; // 5 minutes

export async function generateStaticParams() {
  // Pre-render top 100 products at build time
  const products = await getAllActiveProducts({ page: 0, size: 100 });
  return products.content.map((product) => ({
    slug: product.slug,
  }));
}

export default async function ProductDetailPage({ params }) {
  const product = await getProductBySlug(params.slug);

  const [reviews, related, similar, recommendations] = await Promise.all([
    getProductReviews(product.id, 0, 5),
    getRelatedProducts(product.id, 4),
    getSimilarProducts(product.id, 4),
    getProductBasedRecommendations(product.id, 6)
  ]);

  return (
    <ProductDetailClient
      product={product}
      reviews={reviews}
      related={related}
      similar={similar}
      recommendations={recommendations}
    />
  );
}
```

---

#### 1.4 Category Pages `/category/[slug]`
- **Strategy**: ISR (revalidate: 1800 = 30 minutes)
- **Data Sources**:
  - Category: `getCategoryBySlug(slug)`
  - Products: `getProductsByCategory(categoryId)`
  - Subcategories: `getSubcategories(categoryId)`
  - Category path: `getCategoryPath(categoryId)` (breadcrumb)
- **Why ISR**: Categories don't change often

```typescript
// app/category/[slug]/page.tsx
export const revalidate = 1800; // 30 minutes

export async function generateStaticParams() {
  const categories = await getAllActiveCategories();
  return categories.map((cat) => ({ slug: cat.slug }));
}

export default async function CategoryPage({ params, searchParams }) {
  const category = await getCategoryBySlug(params.slug);

  const [products, subcategories, path] = await Promise.all([
    getProductsByCategory(category.id, searchParams.page || 0, 20),
    getSubcategories(category.id),
    getCategoryPath(category.id)
  ]);

  return (
    <CategoryPageClient
      category={category}
      products={products}
      subcategories={subcategories}
      breadcrumb={path}
    />
  );
}
```

---

#### 1.5 Search Results `/search`
- **Strategy**: SSR (server-side rendered on each request)
- **Data Sources**:
  - Search: `searchProducts(query)` or AI search: `aiSearch()`
- **Why SSR**: Search queries are dynamic and user-specific

```typescript
// app/search/page.tsx
export default async function SearchPage({ searchParams }) {
  const query = searchParams.q || '';

  const products = query.length > 0
    ? await searchProducts(query, searchParams.page || 0)
    : { content: [], totalElements: 0 };

  return <SearchPageClient query={query} products={products} />;
}
```

---

### 2. Authenticated Pages (CSR)

#### 2.1 Shopping Cart `/cart`
- **Strategy**: CSR (client-side only)
- **Data Sources**:
  - Cart: `getCart()`
  - Update: `updateCartItem()`, `removeFromCart()`
  - Coupons: `getAvailableCoupons()`, `applyCoupon()`
- **Why CSR**: User-specific, changes frequently, no SEO needed

```typescript
// app/cart/page.tsx
'use client';

export default function CartPage() {
  const { data: cart, isLoading } = useQuery({
    queryKey: ['cart'],
    queryFn: getCart,
  });

  const updateMutation = useMutation({
    mutationFn: ({ itemId, quantity }) => updateCartItem(itemId, { quantity }),
    onSuccess: () => queryClient.invalidateQueries(['cart']),
  });

  return <CartPageClient cart={cart} updateItem={updateMutation.mutate} />;
}
```

---

#### 2.2 Checkout `/checkout`
- **Strategy**: CSR (client-side only)
- **Data Sources**:
  - Cart: `getCart()`
  - Addresses: `getUserAddresses()`
  - Payment methods: `getPaymentMethods()`
  - Create order: `createOrder()`
  - PayPal: `createPayPalOrder()`, `capturePayPalPayment()`
- **Why CSR**: Sensitive data, authentication required, no SEO

```typescript
// app/checkout/page.tsx
'use client';

export default function CheckoutPage() {
  const { data: cart } = useQuery({ queryKey: ['cart'], queryFn: getCart });
  const { data: addresses } = useQuery({ queryKey: ['addresses'], queryFn: getUserAddresses });
  const { data: paymentMethods } = useQuery({ queryKey: ['payment-methods'], queryFn: getPaymentMethods });

  const createOrderMutation = useMutation({
    mutationFn: createOrder,
    onSuccess: (order) => router.push(`/orders/${order.id}/payment`),
  });

  return <CheckoutPageClient cart={cart} addresses={addresses} paymentMethods={paymentMethods} />;
}
```

---

#### 2.3 User Profile `/profile`
- **Strategy**: CSR (client-side only)
- **Data Sources**:
  - User: `getCurrentUser()`
  - Update: `updateProfile()`
  - Password: `changePassword()`
  - 2FA: `enable2FA()`, `disable2FA()`
  - Spending: `getUserSpending()`
- **Why CSR**: Private user data, no SEO needed

```typescript
// app/profile/page.tsx
'use client';

export default function ProfilePage() {
  const { data: user } = useQuery({ queryKey: ['user'], queryFn: getCurrentUser });
  const { data: spending } = useQuery({ queryKey: ['spending'], queryFn: getUserSpending });

  const updateMutation = useMutation({
    mutationFn: updateProfile,
    onSuccess: () => queryClient.invalidateQueries(['user']),
  });

  return <ProfilePageClient user={user} spending={spending} onUpdate={updateMutation.mutate} />;
}
```

---

#### 2.4 Order History `/orders`
- **Strategy**: CSR (client-side only)
- **Data Sources**:
  - Orders: `getUserOrders()`
  - Statistics: `getUserOrderStatistics()`
- **Why CSR**: Private data, no SEO

```typescript
// app/orders/page.tsx
'use client';

export default function OrdersPage() {
  const [page, setPage] = useState(0);

  const { data: orders } = useQuery({
    queryKey: ['orders', page],
    queryFn: () => getUserOrders(page, 10),
  });

  const { data: stats } = useQuery({
    queryKey: ['order-stats'],
    queryFn: getUserOrderStatistics,
  });

  return <OrdersPageClient orders={orders} stats={stats} page={page} onPageChange={setPage} />;
}
```

---

#### 2.5 Order Detail `/orders/[id]`
- **Strategy**: CSR (client-side only)
- **Data Sources**:
  - Order: `getOrderById(id)`
  - Cancel: `cancelOrder(id)`
  - Payment status: `getPaymentStatus(id)`
- **Why CSR**: Private data, no SEO

```typescript
// app/orders/[id]/page.tsx
'use client';

export default function OrderDetailPage({ params }) {
  const { data: order } = useQuery({
    queryKey: ['order', params.id],
    queryFn: () => getOrderById(Number(params.id)),
  });

  const { data: paymentStatus } = useQuery({
    queryKey: ['payment-status', params.id],
    queryFn: () => getPaymentStatus(Number(params.id)),
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelOrder(Number(params.id)),
    onSuccess: () => queryClient.invalidateQueries(['order', params.id]),
  });

  return <OrderDetailClient order={order} paymentStatus={paymentStatus} />;
}
```

---

#### 2.6 Wishlist `/wishlist`
- **Strategy**: CSR (client-side only)
- **Data Sources**:
  - Wishlist: `getWishlist()`
  - Add: `addToWishlist(productId)`
  - Remove: `removeFromWishlist(productId)`
  - Move to cart: `moveToCart(productId)`
- **Why CSR**: User-specific, no SEO

```typescript
// app/wishlist/page.tsx
'use client';

export default function WishlistPage() {
  const { data: wishlist } = useQuery({
    queryKey: ['wishlist'],
    queryFn: getWishlist,
  });

  const removeMutation = useMutation({
    mutationFn: removeFromWishlist,
    onSuccess: () => queryClient.invalidateQueries(['wishlist']),
  });

  const moveToCartMutation = useMutation({
    mutationFn: ({ productId, quantity }) => moveToCart(productId, quantity),
    onSuccess: () => {
      queryClient.invalidateQueries(['wishlist']);
      queryClient.invalidateQueries(['cart']);
    },
  });

  return <WishlistPageClient wishlist={wishlist} />;
}
```

---

#### 2.7 Addresses `/profile/addresses`
- **Strategy**: CSR (client-side only)
- **Data Sources**:
  - Addresses: `getUserAddresses()`
  - Create: `createAddress()`
  - Update: `updateAddress()`
  - Delete: `deleteAddress()`
  - Set default: `setDefaultAddress()`
- **Why CSR**: Private data, no SEO

```typescript
// app/profile/addresses/page.tsx
'use client';

export default function AddressesPage() {
  const { data: addresses } = useQuery({
    queryKey: ['addresses'],
    queryFn: getUserAddresses,
  });

  const createMutation = useMutation({
    mutationFn: createAddress,
    onSuccess: () => queryClient.invalidateQueries(['addresses']),
  });

  return <AddressesPageClient addresses={addresses} onCreate={createMutation.mutate} />;
}
```

---

#### 2.8 My Reviews `/profile/reviews`
- **Strategy**: CSR (client-side only)
- **Data Sources**:
  - Reviews: `getUserReviews()`
  - Update: `updateReview()`
  - Delete: `deleteReview()`
- **Why CSR**: User-specific data

```typescript
// app/profile/reviews/page.tsx
'use client';

export default function MyReviewsPage() {
  const [page, setPage] = useState(0);

  const { data: reviews } = useQuery({
    queryKey: ['user-reviews', page],
    queryFn: () => getUserReviews(page, 10),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteReview,
    onSuccess: () => queryClient.invalidateQueries(['user-reviews']),
  });

  return <MyReviewsPageClient reviews={reviews} onDelete={deleteMutation.mutate} />;
}
```

---

### 3. AI-Powered Pages

#### 3.1 AI Recommendations `/ai/recommendations`
- **Strategy**: CSR (client-side, authenticated)
- **Data Sources**:
  - Personalized: `getPersonalizedRecommendations()`
  - History-based: `getHistoryBasedRecommendations()`
  - Wishlist-based: `getWishlistBasedRecommendations()`
- **Why CSR**: User-specific AI analysis

```typescript
// app/ai/recommendations/page.tsx
'use client';

export default function AIRecommendationsPage() {
  const { data: recommendations } = useQuery({
    queryKey: ['ai-recommendations'],
    queryFn: getPersonalizedRecommendations,
  });

  return <AIRecommendationsClient recommendations={recommendations} />;
}
```

---

#### 3.2 Outfit Builder `/ai/outfits`
- **Strategy**: CSR (client-side)
- **Data Sources**:
  - Generate outfit: `generateOutfit()`
- **Why CSR**: Interactive, user-specific preferences

```typescript
// app/ai/outfits/page.tsx
'use client';

export default function OutfitBuilderPage() {
  const generateMutation = useMutation({
    mutationFn: generateOutfit,
  });

  return <OutfitBuilderClient onGenerate={generateMutation.mutate} outfits={generateMutation.data} />;
}
```

---

### 4. Static Pages

#### 4.1 About Us `/about`
- **Strategy**: SSG (static at build time)
- **Why SSG**: Content never changes

#### 4.2 Terms & Conditions `/terms`
- **Strategy**: SSG (static at build time)
- **Why SSG**: Content rarely changes

#### 4.3 Privacy Policy `/privacy`
- **Strategy**: SSG (static at build time)
- **Why SSG**: Content rarely changes

#### 4.4 Contact `/contact`
- **Strategy**: SSG (static at build time) + CSR form
- **Why SSG**: Page structure is static, form submission is client-side

---

## Folder Structure

```
frontend/src/app/
├── (auth)/
│   ├── login/
│   │   └── page.tsx                    # CSR - Login page
│   ├── register/
│   │   └── page.tsx                    # CSR - Registration
│   └── forgot-password/
│       └── page.tsx                    # CSR - Password reset
├── shop/
│   ├── page.tsx                        # ISR - Product listing
│   └── [slug]/
│       └── page.tsx                    # ISR - Product detail
├── category/
│   └── [slug]/
│       └── page.tsx                    # ISR - Category page
├── search/
│   └── page.tsx                        # SSR - Search results
├── cart/
│   └── page.tsx                        # CSR - Shopping cart
├── checkout/
│   └── page.tsx                        # CSR - Checkout flow
├── orders/
│   ├── page.tsx                        # CSR - Order history
│   └── [id]/
│       └── page.tsx                    # CSR - Order detail
├── wishlist/
│   └── page.tsx                        # CSR - Wishlist
├── profile/
│   ├── page.tsx                        # CSR - User profile
│   ├── addresses/
│   │   └── page.tsx                    # CSR - Address management
│   └── reviews/
│       └── page.tsx                    # CSR - User's reviews
├── ai/
│   ├── recommendations/
│   │   └── page.tsx                    # CSR - AI recommendations
│   └── outfits/
│       └── page.tsx                    # CSR - Outfit builder
├── about/
│   └── page.tsx                        # SSG - About us
├── terms/
│   └── page.tsx                        # SSG - Terms
├── privacy/
│   └── page.tsx                        # SSG - Privacy
├── contact/
│   └── page.tsx                        # SSG - Contact
└── page.tsx                            # ISR - Homepage
```

---

## API Client Integration

### React Query Setup

Create a React Query provider for data fetching:

```typescript
// lib/providers/query-provider.tsx
'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { useState } from 'react';

export function QueryProvider({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60 * 1000, // 1 minute
            retry: 1,
          },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
}
```

---

## Authentication Guard

Create a higher-order component for protected routes:

```typescript
// components/auth/auth-guard.tsx
'use client';

import { useAuth } from '@/contexts/AuthContext';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !isAuthenticated) {
      router.push('/login?redirect=' + window.location.pathname);
    }
  }, [isAuthenticated, loading, router]);

  if (loading) {
    return <div>Loading...</div>;
  }

  if (!isAuthenticated) {
    return null;
  }

  return <>{children}</>;
}
```

---

## SEO Optimization

### Metadata API

Use Next.js 15 Metadata API for SEO:

```typescript
// app/shop/[slug]/page.tsx
import type { Metadata } from 'next';

export async function generateMetadata({ params }): Promise<Metadata> {
  const product = await getProductBySlug(params.slug);

  return {
    title: `${product.name} | Your Shop`,
    description: product.description,
    openGraph: {
      title: product.name,
      description: product.description,
      images: [product.imageUrl],
      type: 'product',
    },
  };
}
```

---

## Performance Optimization

### 1. Image Optimization

Use Next.js Image component with Cloudinary:

```typescript
import Image from 'next/image';

<Image
  src={product.imageUrl}
  alt={product.name}
  width={500}
  height={500}
  loading="lazy"
  placeholder="blur"
/>
```

### 2. Code Splitting

Dynamic imports for heavy components:

```typescript
import dynamic from 'next/dynamic';

const ReviewForm = dynamic(() => import('@/components/reviews/review-form'));
```

### 3. Parallel Data Fetching

Use `Promise.all()` for parallel requests:

```typescript
const [products, categories, featured] = await Promise.all([
  getAllActiveProducts(),
  getAllActiveCategories(),
  getFeaturedProducts()
]);
```

---

## Summary Table

| Page | Route | Strategy | Revalidate | Data Source |
|------|-------|----------|------------|-------------|
| Homepage | `/` | ISR | 3600s | Featured, New Arrivals, Trending |
| Shop | `/shop` | ISR | 1800s | Products, Categories |
| Product Detail | `/shop/[slug]` | ISR | 300s | Product, Reviews, Related |
| Category | `/category/[slug]` | ISR | 1800s | Category, Products, Subcategories |
| Search | `/search` | SSR | - | Search Results |
| Cart | `/cart` | CSR | - | User Cart |
| Checkout | `/checkout` | CSR | - | Cart, Addresses, Payment |
| Orders | `/orders` | CSR | - | User Orders |
| Order Detail | `/orders/[id]` | CSR | - | Order Details |
| Profile | `/profile` | CSR | - | User Profile |
| Wishlist | `/wishlist` | CSR | - | User Wishlist |
| Addresses | `/profile/addresses` | CSR | - | User Addresses |
| AI Recommendations | `/ai/recommendations` | CSR | - | AI Analysis |
| Outfit Builder | `/ai/outfits` | CSR | - | AI Generation |
| About | `/about` | SSG | - | Static Content |
| Terms | `/terms` | SSG | - | Static Content |
| Privacy | `/privacy` | SSG | - | Static Content |
| Contact | `/contact` | SSG | - | Static Content |

---

## Next Steps

1. Install React Query: `npm install @tanstack/react-query @tanstack/react-query-devtools`
2. Create page components following this architecture
3. Implement SEO metadata for all public pages
4. Add loading states and error boundaries
5. Test rendering strategies with production build
6. Monitor performance with Next.js Analytics
