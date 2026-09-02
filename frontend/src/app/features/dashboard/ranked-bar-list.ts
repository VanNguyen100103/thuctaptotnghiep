import { Component, computed, input } from '@angular/core';

export interface RankedBarRow {
  label: string;
  value: number;
  formattedValue: string;
}

@Component({
  selector: 'app-ranked-bar-list',
  standalone: true,
  templateUrl: './ranked-bar-list.html',
})
export class RankedBarList {
  readonly title = input.required<string>();
  readonly rows = input<RankedBarRow[]>([]);
  readonly emptyMessage = input('Chưa có dữ liệu.');

  private readonly maxValue = computed(() => Math.max(1, ...this.rows().map((r) => r.value)));

  barWidthPercent(value: number): number {
    return (value / this.maxValue()) * 100;
  }
}
