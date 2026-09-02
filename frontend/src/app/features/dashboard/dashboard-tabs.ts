import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

const UPCOMING_TABS = [
  'Hàng hóa',
  'Mua hàng',
  'Đơn hàng',
  'Khách hàng',
  'Nhân viên',
  'Sổ quỹ',
  'Báo cáo',
  'Bán online',
];

@Component({
  selector: 'app-dashboard-tabs',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard-tabs.html',
})
export class DashboardTabs {
  readonly upcomingTabs = UPCOMING_TABS;
}
