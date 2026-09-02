import { Component, input } from '@angular/core';

import { RecentActivity as RecentActivityItem } from './dashboard.models';

/** "vài giây trước" / "5 phút trước" / "2 giờ trước" / "3 ngày trước" style, matching the KiotViet reference. */
function relativeTimeVi(iso: string): string {
  const seconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return 'vài giây trước';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.floor(hours / 24);
  return `${days} ngày trước`;
}

@Component({
  selector: 'app-recent-activity',
  standalone: true,
  templateUrl: './recent-activity.html',
})
export class RecentActivity {
  readonly activities = input<RecentActivityItem[]>([]);

  relativeTime(iso: string): string {
    return relativeTimeVi(iso);
  }
}
