import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { of, switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { DashboardStatsService } from './dashboard-stats.service';
import {
  ApiState,
  LowStockProduct,
  OrderStatusStat,
  OverviewStats,
  RecentActivity as RecentActivityModel,
  SalesPeriod,
  SalesStats,
  TopProduct,
} from './dashboard.models';
import { RankedBarList, RankedBarRow } from './ranked-bar-list';
import { RecentActivity } from './recent-activity';
import { RevenueChart } from './revenue-chart';
import { StatCard } from './stat-card';

const INITIAL_STATE = INITIAL_API_STATE;

@Component({
  selector: 'app-dashboard-overview',
  standalone: true,
  imports: [StatCard, RevenueChart, RankedBarList, RecentActivity],
  templateUrl: './dashboard-overview.html',
})
export class DashboardOverview {
  private readonly statsService = inject(DashboardStatsService);
  private readonly vndCurrency = new VndCurrencyPipe();

  readonly overviewState = toSignal(toApiState<OverviewStats>(this.statsService.getOverview()), {
    initialValue: INITIAL_STATE as ApiState<OverviewStats>,
  });

  readonly todaySalesState = toSignal(toApiState<SalesStats>(this.statsService.getSales('today')), {
    initialValue: INITIAL_STATE as ApiState<SalesStats>,
  });

  readonly topProductsState = toSignal(
    toApiState(this.statsService.getTopProducts().pipe(switchMap((res) => of(res.topProducts)))),
    { initialValue: INITIAL_STATE as ApiState<TopProduct[]> },
  );

  readonly lowStockState = toSignal(
    toApiState(this.statsService.getLowStock().pipe(switchMap((res) => of(res.products)))),
    { initialValue: INITIAL_STATE as ApiState<LowStockProduct[]> },
  );

  readonly orderStatusState = toSignal(
    toApiState(this.statsService.getOrderStatusStats().pipe(switchMap((res) => of(res.orderStatusStats)))),
    { initialValue: INITIAL_STATE as ApiState<OrderStatusStat[]> },
  );

  readonly recentActivitiesState = toSignal(
    toApiState(this.statsService.getRecentActivities().pipe(switchMap((res) => of(res.activities)))),
    { initialValue: INITIAL_STATE as ApiState<RecentActivityModel[]> },
  );

  readonly chartPeriod = signal<SalesPeriod>('today');
  readonly chartSalesState = toSignal(
    toObservable(this.chartPeriod).pipe(switchMap((period) => toApiState(this.statsService.getSales(period)))),
    { initialValue: INITIAL_STATE as ApiState<SalesStats> },
  );

  readonly revenueToday = computed(() => this.vndCurrency.transform(this.overviewState().data?.revenue.today ?? 0));
  readonly ordersToday = computed(() => String(this.todaySalesState().data?.totalOrders ?? 0));
  readonly averageOrderToday = computed(() =>
    this.vndCurrency.transform(this.todaySalesState().data?.averageOrderValue ?? 0),
  );
  readonly refundedTotal = computed(() => {
    const refunded = this.orderStatusState().data?.find((s) => s.status === 'REFUNDED');
    return this.vndCurrency.transform(refunded?.totalOrderValue ?? 0);
  });

  readonly topProductRows = computed<RankedBarRow[]>(() =>
    (this.topProductsState().data ?? []).map((p) => ({
      label: p.productName,
      value: p.unitsSold,
      formattedValue: `${p.unitsSold} đã bán`,
    })),
  );

  readonly lowStockRows = computed<RankedBarRow[]>(() =>
    [...(this.lowStockState().data ?? [])]
      .sort((a, b) => a.stockQuantity - b.stockQuantity)
      .map((p) => ({
        label: p.productName,
        value: p.stockQuantity,
        formattedValue: `${p.stockQuantity} còn lại`,
      })),
  );
}
