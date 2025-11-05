/**
 * Product Detail Page
 * Strategy: ISR (Incremental Static Regeneration)
 * Revalidate: 1 hour
 */

import { getProductBySlug } from '@/lib/api/products';
import { getProductReviews, getProductReviewStatistics } from '@/lib/api/reviews';
import ProductDetailClient from '@/components/product-detail/ProductDetailClient';
import { notFound } from 'next/navigation';

// ISR: Revalidate every 1 hour
export const revalidate = 3600;

interface ProductDetailPageProps {
  params: {
    slug: string;
  };
}

export default async function ProductDetailPage({ params }: ProductDetailPageProps) {
  try {
    const product = await getProductBySlug(params.slug);

    // Fetch reviews and statistics in parallel
    const [reviews, apiReviewStats] = await Promise.all([
      getProductReviews(product.id, 0, 10).catch(() => ({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 0,
        number: 0,
        averageRating: 0,
        ratingDistribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 }
      })),
      getProductReviewStatistics(product.id).catch(() => ({
        totalReviews: 0,
        averageRating: 0,
        ratingDistribution: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 },
        verifiedPurchaseCount: 0
      }))
    ]);

    // Transform API stats to match component interface
    const reviewStats = {
      averageRating: apiReviewStats.averageRating,
      totalReviews: apiReviewStats.totalReviews,
      ratingDistribution: {
        '1': apiReviewStats.ratingDistribution[1],
        '2': apiReviewStats.ratingDistribution[2],
        '3': apiReviewStats.ratingDistribution[3],
        '4': apiReviewStats.ratingDistribution[4],
        '5': apiReviewStats.ratingDistribution[5],
      },
      ratingPercentages: {
        '1': apiReviewStats.totalReviews > 0 ? (apiReviewStats.ratingDistribution[1] / apiReviewStats.totalReviews) * 100 : 0,
        '2': apiReviewStats.totalReviews > 0 ? (apiReviewStats.ratingDistribution[2] / apiReviewStats.totalReviews) * 100 : 0,
        '3': apiReviewStats.totalReviews > 0 ? (apiReviewStats.ratingDistribution[3] / apiReviewStats.totalReviews) * 100 : 0,
        '4': apiReviewStats.totalReviews > 0 ? (apiReviewStats.ratingDistribution[4] / apiReviewStats.totalReviews) * 100 : 0,
        '5': apiReviewStats.totalReviews > 0 ? (apiReviewStats.ratingDistribution[5] / apiReviewStats.totalReviews) * 100 : 0,
      }
    };

    return <ProductDetailClient product={product} reviews={reviews} reviewStats={reviewStats} />;
  } catch (error) {
    console.error('Error fetching product:', error);
    notFound();
  }
}

// Generate metadata for SEO
export async function generateMetadata({ params }: ProductDetailPageProps) {
  try {
    const product = await getProductBySlug(params.slug);

    return {
      title: product.metaTitle || `${product.name} | Coolmate Fashion`,
      description: product.metaDescription || product.shortDescription || product.description,
      keywords: product.metaKeywords,
      openGraph: {
        title: product.metaTitle || product.name,
        description: product.metaDescription || product.shortDescription || product.description,
        images: product.images?.map((img) => img.imageUrl) || [],
      },
    };
  } catch (error) {
    return {
      title: 'Sản phẩm không tìm thấy',
      description: 'Sản phẩm bạn đang tìm kiếm không tồn tại.',
    };
  }
}
