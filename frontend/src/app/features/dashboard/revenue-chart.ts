import { Component, DestroyRef, ElementRef, effect, inject, input, model, viewChild } from '@angular/core';
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  LinearScale,
  Tooltip,
} from 'chart.js';

import { SalesPeriod } from './dashboard.models';

Chart.register(BarController, BarElement, CategoryScale, LinearScale, Tooltip);

const PERIOD_OPTIONS: { value: SalesPeriod; label: string }[] = [
  { value: 'today', label: 'Hôm nay' },
  { value: '7days', label: '7 ngày qua' },
  { value: '30days', label: '30 ngày qua' },
  { value: '90days', label: '90 ngày qua' },
  { value: 'year', label: 'Năm nay' },
];

@Component({
  selector: 'app-revenue-chart',
  standalone: true,
  templateUrl: './revenue-chart.html',
})
export class RevenueChart {
  readonly salesByDate = input<Record<string, number> | null>(null);
  readonly period = model<SalesPeriod>('today');
  readonly periodOptions = PERIOD_OPTIONS;

  private readonly canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('canvas');
  private chart: Chart | null = null;

  constructor() {
    effect(() => {
      const canvasEl = this.canvasRef()?.nativeElement;
      const data = this.salesByDate();
      if (!canvasEl || !data) {
        return;
      }

      const labels = Object.keys(data);
      const values = Object.values(data);

      if (this.chart) {
        this.chart.data.labels = labels;
        this.chart.data.datasets[0].data = values;
        this.chart.update();
      } else {
        this.chart = new Chart(canvasEl, {
          type: 'bar',
          data: {
            labels,
            datasets: [
              {
                data: values,
                backgroundColor: '#2563eb',
                borderRadius: 4,
                maxBarThickness: 24,
              },
            ],
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
              x: { grid: { display: false } },
              y: { beginAtZero: true, grid: { color: '#e5e7eb' } },
            },
          },
        });
      }
    });

    inject(DestroyRef).onDestroy(() => this.chart?.destroy());
  }

  onPeriodChange(event: Event): void {
    this.period.set((event.target as HTMLSelectElement).value as SalesPeriod);
  }
}
