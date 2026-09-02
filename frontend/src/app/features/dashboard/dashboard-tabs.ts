import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

const UPCOMING_TABS = [
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
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './dashboard-tabs.html',
})
export class DashboardTabs {
  readonly upcomingTabs = UPCOMING_TABS;
}
