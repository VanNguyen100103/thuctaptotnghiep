'use client';

/**
 * Product Clustering Analytics Page
 * Uses CSR (Client-Side Rendering) for real-time data fetching
 */

import { useEffect, useState } from 'react';
import { getProductClusters, ProductClusteringResponse, ProductMetrics } from '@/lib/api/clustering';
import { ApiError } from '@/lib/api/client';
import { cleanAIText } from '@/utils/textUtils';

// Define cluster types based on performance
type ClusterType = 'bestSellers' | 'moderatePerformers' | 'lowPerformers';

interface ClusteredProduct extends ProductMetrics {
  cluster: ClusterType;
}

// Cluster rules (matching backend)
const CLUSTER_RULES = {
  bestSellers: { label: 'Best Sellers', color: 'bg-green-100 text-green-800' },
  moderatePerformers: { label: 'Moderate Performers', color: 'bg-yellow-100 text-yellow-800' },
  lowPerformers: { label: 'Low Performers', color: 'bg-red-100 text-red-800' },
};

function determineCluster(product: ProductMetrics): ClusterType {
  // Best Sellers: salesVolume > 50 OR revenue > 5,000,000 VND
  if (product.salesVolume > 50 || product.revenue > 5000000) {
    return 'bestSellers';
  }
  // Moderate Performers: 10 < salesVolume <= 50 OR 1,000,000 < revenue <= 5,000,000 VND
  if (
    (product.salesVolume > 10 && product.salesVolume <= 50) ||
    (product.revenue > 1000000 && product.revenue <= 5000000)
  ) {
    return 'moderatePerformers';
  }
  // Low Performers: salesVolume <= 10 OR revenue <= 1,000,000 VND
  return 'lowPerformers';
}

export default function ProductClusteringPage() {
  const [data, setData] = useState<ProductClusteringResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedCluster, setSelectedCluster] = useState<ClusterType | 'all'>('all');

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const response = await getProductClusters();
        setData(response);
        setError(null);
      } catch (err) {
        const apiError = err as ApiError;
        setError(apiError.message || 'Failed to fetch product clustering data');
        console.error('Error fetching product clusters:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-green-500 mx-auto"></div>
          <p className="mt-4 text-gray-600">Đang tải dữ liệu phân nhóm sản phẩm...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-6">
        <h3 className="text-lg font-semibold text-red-800 mb-2">Error Loading Data</h3>
        <p className="text-red-600">{error}</p>
        <button
          onClick={() => window.location.reload()}
          className="mt-4 bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700"
        >
          Retry
        </button>
      </div>
    );
  }

  if (!data || !data.success) {
    return (
      <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-6">
        <p className="text-yellow-800">No clustering data available</p>
      </div>
    );
  }

  // Cluster products
  const clusteredProducts: ClusteredProduct[] = data.products.map(product => ({
    ...product,
    cluster: determineCluster(product),
  }));

  // Calculate cluster statistics
  const clusterStats = {
    bestSellers: clusteredProducts.filter(p => p.cluster === 'bestSellers'),
    moderatePerformers: clusteredProducts.filter(p => p.cluster === 'moderatePerformers'),
    lowPerformers: clusteredProducts.filter(p => p.cluster === 'lowPerformers'),
  };

  const filteredProducts = selectedCluster === 'all'
    ? clusteredProducts
    : clusterStats[selectedCluster];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white shadow rounded-lg p-6">
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Product Clustering Analytics</h2>
        <p className="text-gray-600">
          AI-powered product segmentation based on sales performance
        </p>
        <div className="mt-4 flex items-center text-sm text-gray-500">
          <svg className="h-5 w-5 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          Last updated: {new Date(data.timestamp).toLocaleString()}
        </div>
      </div>

      {/* Cluster Summary Cards */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
        <div className="bg-white shadow rounded-lg p-6 border-l-4 border-green-500">
          <h3 className="text-sm font-medium text-gray-500">Best Sellers</h3>
          <p className="mt-2 text-3xl font-bold text-gray-900">{clusterStats.bestSellers.length}</p>
          <p className="mt-1 text-xs text-gray-600">High sales volume or revenue</p>
        </div>
        <div className="bg-white shadow rounded-lg p-6 border-l-4 border-yellow-500">
          <h3 className="text-sm font-medium text-gray-500">Moderate Performers</h3>
          <p className="mt-2 text-3xl font-bold text-gray-900">{clusterStats.moderatePerformers.length}</p>
          <p className="mt-1 text-xs text-gray-600">Average performance</p>
        </div>
        <div className="bg-white shadow rounded-lg p-6 border-l-4 border-red-500">
          <h3 className="text-sm font-medium text-gray-500">Low Performers</h3>
          <p className="mt-2 text-3xl font-bold text-gray-900">{clusterStats.lowPerformers.length}</p>
          <p className="mt-1 text-xs text-gray-600">Low sales volume or revenue</p>
        </div>
      </div>

      {/* AI Analysis */}
      <div className="bg-gradient-to-r from-green-50 to-blue-50 shadow rounded-lg p-6">
        <div className="flex items-center mb-4">
          <svg className="h-6 w-6 text-green-600 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
          </svg>
          <h3 className="text-lg font-semibold text-gray-900">AI Insights (Gemini)</h3>
        </div>
        <div className="bg-white rounded p-4 text-gray-700 whitespace-pre-wrap">
          {cleanAIText(data.analysis)}
        </div>
      </div>

      {/* Filter and Product Table */}
      <div className="bg-white shadow rounded-lg p-6">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-semibold text-gray-900">Product Details</h3>
          <select
            value={selectedCluster}
            onChange={(e) => setSelectedCluster(e.target.value as ClusterType | 'all')}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm"
          >
            <option value="all">All Products ({data.totalProducts})</option>
            <option value="bestSellers">Best Sellers ({clusterStats.bestSellers.length})</option>
            <option value="moderatePerformers">Moderate Performers ({clusterStats.moderatePerformers.length})</option>
            <option value="lowPerformers">Low Performers ({clusterStats.lowPerformers.length})</option>
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  ID
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Product Name
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Category
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Price (VND)
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Sales Volume
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Revenue (VND)
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Rating
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Cluster
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredProducts.map((product) => (
                <tr key={product.productId} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {product.productId}
                  </td>
                  <td className="px-6 py-4 text-sm font-medium text-gray-900">
                    {product.name}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {product.category}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {product.price.toLocaleString('vi-VN')}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {product.salesVolume}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {product.revenue.toLocaleString('vi-VN')}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    <div className="flex items-center">
                      <span className="mr-1">{product.averageRating.toFixed(1)}</span>
                      <svg className="h-4 w-4 text-yellow-400" fill="currentColor" viewBox="0 0 20 20">
                        <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                      </svg>
                      <span className="ml-1 text-xs">({product.reviewCount})</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${CLUSTER_RULES[product.cluster].color}`}>
                      {CLUSTER_RULES[product.cluster].label}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Clustering Rules Reference */}
      <div className="bg-white shadow rounded-lg p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Clustering Rules</h3>
        <div className="space-y-2 text-sm text-gray-600">
          <p><strong>Best Sellers:</strong> {data.clusteringRules.bestSellers}</p>
          <p><strong>Moderate Performers:</strong> {data.clusteringRules.moderatePerformers}</p>
          <p><strong>Low Performers:</strong> {data.clusteringRules.lowPerformers}</p>
        </div>
      </div>
    </div>
  );
}
