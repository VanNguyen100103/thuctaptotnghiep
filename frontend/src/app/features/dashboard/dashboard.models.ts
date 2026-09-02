export interface ApiState<T> {
  data: T | null;
  error: string | null;
}

export type SalesPeriod = 'today' | '7days' | '30days' | '90days' | 'year';

export interface OverviewStats {
  products: { total: number; active: number; inactive: number; outOfStock: number };
  orders: { total: number; pending: number; processing: number; shipped: number };
  revenue: { total: number; today: number };
}

export interface SalesStats {
  period: SalesPeriod;
  totalSales: number;
  totalOrders: number;
  averageOrderValue: number;
  salesByDate: Record<string, number>;
}

export interface TopProduct {
  productId: number;
  productName: string;
  unitsSold: number;
  revenue: number;
}

export interface OrderStatusStat {
  status: string;
  count: number;
  totalOrderValue: number;
  confirmedRevenue: number;
  isConfirmedRevenue: boolean;
}

export interface RecentActivity {
  type: 'ORDER' | 'USER';
  timestamp: string;
  description: string;
  status?: string;
  amount?: number;
  email?: string;
}

export interface LowStockProduct {
  productId: number;
  productName: string;
  sku: string;
  stockQuantity: number;
}
