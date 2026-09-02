import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  LowStockProduct,
  OrderStatusStat,
  OverviewStats,
  RecentActivity,
  SalesPeriod,
  SalesStats,
  TopProduct,
} from './dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardStatsService {
  constructor(private readonly http: HttpClient) {}

  getOverview(): Observable<OverviewStats> {
    return this.http.get<OverviewStats>(`${environment.apiUrl}/store/dashboard/overview`);
  }

  getSales(period: SalesPeriod): Observable<SalesStats> {
    return this.http.get<SalesStats>(`${environment.apiUrl}/store/dashboard/sales`, {
      params: { period, size: 1000 },
    });
  }

  getTopProducts(limit = 10): Observable<{ topProducts: TopProduct[] }> {
    return this.http.get<{ topProducts: TopProduct[] }>(`${environment.apiUrl}/store/dashboard/top-products`, {
      params: { limit },
    });
  }

  getOrderStatusStats(): Observable<{ orderStatusStats: OrderStatusStat[] }> {
    return this.http.get<{ orderStatusStats: OrderStatusStat[] }>(
      `${environment.apiUrl}/store/dashboard/order-status-stats`,
    );
  }

  getRecentActivities(limit = 20): Observable<{ activities: RecentActivity[] }> {
    return this.http.get<{ activities: RecentActivity[] }>(
      `${environment.apiUrl}/store/dashboard/recent-activities`,
      { params: { limit } },
    );
  }

  getLowStock(threshold = 10, limit = 50): Observable<{ products: LowStockProduct[] }> {
    return this.http.get<{ products: LowStockProduct[] }>(`${environment.apiUrl}/store/dashboard/low-stock`, {
      params: { threshold, limit },
    });
  }
}
