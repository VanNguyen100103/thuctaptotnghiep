import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { ActionErrorBanner } from './action-error-banner';
import { ActionError, toActionError } from './subscription-error.util';
import { TAX_STATUS_LABELS, TaxDeclarationRow, TaxYearSummary, declarationRoundLabel } from './tax.models';
import { TaxService } from './tax.service';

/**
 * "Tờ khai 01/CNKD" - the declaration screen of Thuế & Kế toán.
 *
 * One year at a time, as KiotViet shows it: four cumulative cards across the
 * top, then a row per declaration period with its deadline, its state and
 * what it owes. Everything comes from a single call - the server walks the
 * calendar and the invoices together, so the screen never has to know what a
 * quarter is or which sales count as revenue.
 *
 * The status cell is the only writable thing here. It is a plain select
 * rather than a "Nộp tờ khai" button because filing actually happens on the
 * tax authority's own portal; what this records is that the shop HAS filed,
 * which is the fact the list needs to stop nagging about a deadline.
 */
@Component({
  selector: 'app-tax-declaration-list',
  standalone: true,
  imports: [ActionErrorBanner, RouterLink, VndCurrencyPipe],
  templateUrl: './tax-declaration-list.html',
})
export class TaxDeclarationList {
  private readonly taxService = inject(TaxService);

  readonly statusLabels = TAX_STATUS_LABELS;
  readonly roundLabel = declarationRoundLabel;

  readonly year = signal(new Date().getFullYear());
  readonly summary = signal<TaxYearSummary | null>(null);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly actionError = signal<ActionError | null>(null);

  /** Which row's select is mid-flight, so it can be disabled without freezing the whole table. */
  readonly savingPeriod = signal<number | null>(null);

  readonly rows = computed(() => this.summary()?.periods ?? []);

  /** KiotViet prints a range even when everything fits on one page; with at most 12 periods in a year, it always does. */
  readonly rangeLabel = computed(() => {
    const total = this.rows().length;
    return total === 0 ? '0 trong 0' : `1-${total} trong ${total}`;
  });

  /** A khoán household pays an assessed amount and files nothing periodically - the table would be a lie. */
  readonly isLumpSum = computed(() => this.summary()?.taxMethod === 'KHOAN');

  constructor() {
    effect(() => {
      // Refetches on a year change and on anything that moves the figures -
      // a return marked nộp, or a different ngành nghề saved in Thiết lập.
      this.year();
      this.taxService.changed();
      this.load();
    });
  }

  private load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.taxService.declarations(this.year()).subscribe({
      next: (summary) => {
        this.loading.set(false);
        this.summary.set(summary);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(toActionError(err).message);
      },
    });
  }

  onYearChange(value: string): void {
    const parsed = Number(value);
    if (!Number.isNaN(parsed)) {
      this.year.set(parsed);
    }
  }

  onStatusChange(row: TaxDeclarationRow, value: string): void {
    const submitted = value === 'DA_NOP';
    if (submitted === (row.status === 'DA_NOP')) {
      return;
    }
    this.actionError.set(null);
    this.savingPeriod.set(row.periodNumber);
    this.taxService.setSubmitted(row.year, row.periodNumber, submitted).subscribe({
      next: () => this.savingPeriod.set(null),
      error: (err: HttpErrorResponse) => {
        this.savingPeriod.set(null);
        this.actionError.set(toActionError(err));
        // The select has already moved to the value the server rejected;
        // re-reading the year puts every row back on what is stored.
        this.load();
      },
    });
  }

  detailLink(row: TaxDeclarationRow): string {
    return `/dashboard/tax/declarations/01-cnkd/${row.year}/${row.periodNumber}`;
  }

  /** "30/04/2026" - the deadline as the list prints it; null while the period is still open. */
  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('vi-VN') : '';
  }
}
