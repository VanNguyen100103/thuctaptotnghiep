import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { DashboardStatsService } from './dashboard-stats.service';

describe('DashboardStatsService', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getOverview() calls GET /store/dashboard/overview', () => {
    const service = TestBed.inject(DashboardStatsService);

    service.getOverview().subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/store/dashboard/overview`);
    expect(req.request.method).toBe('GET');
    req.flush({
      products: { total: 1, active: 1, inactive: 0, outOfStock: 0 },
      orders: { total: 1, pending: 0, processing: 0, shipped: 0 },
      revenue: { total: 100, today: 10 },
    });
  });

  it('getSales() sends period and a large page size', () => {
    const service = TestBed.inject(DashboardStatsService);

    service.getSales('7days').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/store/dashboard/sales`);
    expect(req.request.params.get('period')).toBe('7days');
    expect(req.request.params.get('size')).toBe('1000');
    req.flush({ period: '7days', totalSales: 0, totalOrders: 0, averageOrderValue: 0, salesByDate: {} });
  });

  it('getTopProducts() sends limit', () => {
    const service = TestBed.inject(DashboardStatsService);

    service.getTopProducts(5).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/store/dashboard/top-products`);
    expect(req.request.params.get('limit')).toBe('5');
    req.flush({ topProducts: [] });
  });

  it('getOrderStatusStats() calls GET /store/dashboard/order-status-stats', () => {
    const service = TestBed.inject(DashboardStatsService);

    service.getOrderStatusStats().subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/store/dashboard/order-status-stats`);
    expect(req.request.method).toBe('GET');
    req.flush({ orderStatusStats: [] });
  });

  it('getRecentActivities() sends limit', () => {
    const service = TestBed.inject(DashboardStatsService);

    service.getRecentActivities(10).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/store/dashboard/recent-activities`);
    expect(req.request.params.get('limit')).toBe('10');
    req.flush({ activities: [] });
  });

  it('getLowStock() sends threshold and limit', () => {
    const service = TestBed.inject(DashboardStatsService);

    service.getLowStock(5, 20).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/store/dashboard/low-stock`);
    expect(req.request.params.get('threshold')).toBe('5');
    expect(req.request.params.get('limit')).toBe('20');
    req.flush({ products: [] });
  });
});
