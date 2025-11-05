'use client';

/**
 * User Clustering Analytics Page
 * Uses CSR (Client-Side Rendering) for real-time data fetching
 */

import { useEffect, useState } from 'react';
import { getUserClusters, UserClusteringResponse, UserMetrics } from '@/lib/api/clustering';
import { ApiError } from '@/lib/api/client';
import { cleanAIText } from '@/utils/textUtils';

// Define cluster types based on spending
type ClusterType = 'lowValue' | 'mediumValue' | 'highValue';

interface ClusteredUser extends UserMetrics {
  cluster: ClusterType;
}

// Cluster boundaries (matching backend rules)
const CLUSTER_RULES = {
  lowValue: { max: 200000, label: 'Low Value', color: 'bg-red-100 text-red-800' },
  mediumValue: { min: 200000, max: 500000, label: 'Medium Value', color: 'bg-yellow-100 text-yellow-800' },
  highValue: { min: 500000, label: 'High Value', color: 'bg-green-100 text-green-800' },
};

function determineCluster(spending: number): ClusterType {
  if (spending < 200000) return 'lowValue';
  if (spending < 500000) return 'mediumValue';
  return 'highValue';
}

export default function UserClusteringPage() {
  const [data, setData] = useState<UserClusteringResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedCluster, setSelectedCluster] = useState<ClusterType | 'all'>('all');

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const response = await getUserClusters();
        setData(response);
        setError(null);
      } catch (err) {
        const apiError = err as ApiError;
        setError(apiError.message || 'Failed to fetch user clustering data');
        console.error('Error fetching user clusters:', err);
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
          <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
          <p className="mt-4 text-gray-600">Đang tải dữ liệu phân nhóm người dùng...</p>
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

  // Cluster users
  const clusteredUsers: ClusteredUser[] = data.users.map(user => ({
    ...user,
    cluster: determineCluster(user.spending),
  }));

  // Calculate cluster statistics
  const clusterStats = {
    lowValue: clusteredUsers.filter(u => u.cluster === 'lowValue'),
    mediumValue: clusteredUsers.filter(u => u.cluster === 'mediumValue'),
    highValue: clusteredUsers.filter(u => u.cluster === 'highValue'),
  };

  const filteredUsers = selectedCluster === 'all'
    ? clusteredUsers
    : clusterStats[selectedCluster];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white shadow rounded-lg p-6">
        <h2 className="text-2xl font-bold text-gray-900 mb-2">User Clustering Analytics</h2>
        <p className="text-gray-600">
          AI-powered user segmentation based on spending behavior
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
        <div className="bg-white shadow rounded-lg p-6 border-l-4 border-red-500">
          <h3 className="text-sm font-medium text-gray-500">Low Value Users</h3>
          <p className="mt-2 text-3xl font-bold text-gray-900">{clusterStats.lowValue.length}</p>
          <p className="mt-1 text-xs text-gray-600">&lt; 200,000 VND</p>
        </div>
        <div className="bg-white shadow rounded-lg p-6 border-l-4 border-yellow-500">
          <h3 className="text-sm font-medium text-gray-500">Medium Value Users</h3>
          <p className="mt-2 text-3xl font-bold text-gray-900">{clusterStats.mediumValue.length}</p>
          <p className="mt-1 text-xs text-gray-600">200,000 - 500,000 VND</p>
        </div>
        <div className="bg-white shadow rounded-lg p-6 border-l-4 border-green-500">
          <h3 className="text-sm font-medium text-gray-500">High Value Users</h3>
          <p className="mt-2 text-3xl font-bold text-gray-900">{clusterStats.highValue.length}</p>
          <p className="mt-1 text-xs text-gray-600">&gt; 500,000 VND</p>
        </div>
      </div>

      {/* AI Analysis */}
      <div className="bg-gradient-to-r from-purple-50 to-blue-50 shadow rounded-lg p-6">
        <div className="flex items-center mb-4">
          <svg className="h-6 w-6 text-purple-600 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
          </svg>
          <h3 className="text-lg font-semibold text-gray-900">AI Insights (Gemini)</h3>
        </div>
        <div className="bg-white rounded p-4 text-gray-700 whitespace-pre-wrap">
          {cleanAIText(data.analysis)}
        </div>
      </div>

      {/* Filter and User Table */}
      <div className="bg-white shadow rounded-lg p-6">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-semibold text-gray-900">User Details</h3>
          <select
            value={selectedCluster}
            onChange={(e) => setSelectedCluster(e.target.value as ClusterType | 'all')}
            className="border border-gray-300 rounded-md px-3 py-2 text-sm"
          >
            <option value="all">All Users ({data.totalUsers})</option>
            <option value="lowValue">Low Value ({clusterStats.lowValue.length})</option>
            <option value="mediumValue">Medium Value ({clusterStats.mediumValue.length})</option>
            <option value="highValue">High Value ({clusterStats.highValue.length})</option>
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  User ID
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Username
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Orders
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Spending (VND)
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Reviews
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Cluster
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredUsers.map((user) => (
                <tr key={user.userId} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {user.userId}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    {user.username}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {user.orders}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {user.spending.toLocaleString('vi-VN')}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {user.reviews}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${CLUSTER_RULES[user.cluster].color}`}>
                      {CLUSTER_RULES[user.cluster].label}
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
          <p><strong>Low Value:</strong> {data.clusteringRules.lowValue}</p>
          <p><strong>Medium Value:</strong> {data.clusteringRules.mediumValue}</p>
          <p><strong>High Value:</strong> {data.clusteringRules.highValue}</p>
        </div>
      </div>
    </div>
  );
}
