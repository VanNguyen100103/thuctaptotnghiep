import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { TAX_STATUS_LABELS, TaxDeclarationDetail as TaxDeclarationDetailDTO, declarationRoundLabel } from './tax.models';
import { TaxService } from './tax.service';

/**
 * "Xem chi tiết" on one period of tờ khai 01/CNKD - the form itself, and the
 * working behind its one figure.
 *
 * The working is the reason this screen exists rather than a PDF export. A
 * shop about to send a number to the tax office asks where it came from, and
 * "45.802.250 = 38 hóa đơn tại quầy + 4 đơn online" is an answer it can check
 * against its own tills. The filed-vs-live comparison at the bottom answers
 * the follow-up nobody thinks to ask until it bites: whether the books have
 * moved since the return went in.
 */
@Component({
  selector: 'app-tax-declaration-detail',
  standalone: true,
  imports: [RouterLink, VndCurrencyPipe],
  templateUrl: './tax-declaration-detail.html',
})
export class TaxDeclarationDetail {
  private readonly taxService = inject(TaxService);

  private readonly route = inject(ActivatedRoute);

  // Read as signals off paramMap rather than the route snapshot, so moving
  // between two periods reuses the component instead of remounting it -
  // the same pattern PurchaseOrderForm uses for its :id.
  private readonly paramMap = toSignal(this.route.paramMap, { requireSync: true });

  readonly year = computed(() => Number(this.paramMap()!.get('year')) || new Date().getFullYear());
  readonly period = computed(() => Number(this.paramMap()!.get('period')) || 1);

  readonly statusLabels = TAX_STATUS_LABELS;
  readonly roundLabel = declarationRoundLabel;

  readonly detail = signal<TaxDeclarationDetailDTO | null>(null);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);

  /** Revenue the tills show today for a period already filed, so the two can be compared. */
  readonly liveRevenue = computed(() => {
    const data = this.detail();
    return data ? data.posRevenue + data.onlineRevenue : 0;
  });

  /** Non-zero only on a filed period whose books have moved since - the whole point of the snapshot. */
  readonly revenueDrift = computed(() => {
    const data = this.detail();
    return data && data.frozen ? this.liveRevenue() - data.taxableRevenue : 0;
  });

  constructor() {
    effect(() => {
      this.year();
      this.period();
      this.taxService.changed();
      this.load();
    });
  }

  private load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.taxService.declarationDetail(this.year(), this.period()).subscribe({
      next: (detail) => {
        this.loading.set(false);
        this.detail.set(detail);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.detail.set(null);
        this.loadError.set(err.error?.error ?? 'Không tải được chi tiết tờ khai');
      },
    });
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('vi-VN') : '';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('vi-VN') : '';
  }

  /** The browser's own print dialog; the dashboard chrome is print:hidden already, so the form comes out on its own. */
  print(): void {
    window.print();
  }
}
