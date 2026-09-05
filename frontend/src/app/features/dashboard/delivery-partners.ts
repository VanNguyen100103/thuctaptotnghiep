import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { toApiState } from './api-state.util';
import { INTEGRATED_CARRIERS } from './delivery-partner.models';
import { GhnShipmentFormModal } from './ghn-shipment-form-modal';
import { GHN_STATUS_LABELS, GhnShipmentDTO, ghnStatusLabel } from './ghn-shipment.models';
import { GhnShipmentService } from './ghn-shipment.service';

type MainTab = 'integration' | 'other';
type IntegrationSubTab = 'info' | 'shipping-history' | 'reconciliation-history';

/** "Đối tác giao hàng" page, laid out like KiotViet's own: an Integration tab (carrier
 * overview + shipping/reconciliation history) and an "Other" tab for manually-added
 * partners. Nothing here has a backend counterpart yet EXCEPT GHN's shipping-history
 * sub-tab, which creates and tracks real GHN sandbox shipments (see GhnShipment's
 * backend doc comment for why it's a standalone test tool rather than wired to real
 * Orders) - every other control that would need a backend stays disabled with a
 * "Sắp ra mắt" tooltip. */
@Component({
  selector: 'app-delivery-partners',
  standalone: true,
  imports: [VndCurrencyPipe, DatePipe, GhnShipmentFormModal],
  templateUrl: './delivery-partners.html',
})
export class DeliveryPartners {
  private readonly ghnShipmentService = inject(GhnShipmentService);

  readonly carriers = INTEGRATED_CARRIERS;
  readonly statusOptions = Object.entries(GHN_STATUS_LABELS);
  readonly ghnStatusLabel = ghnStatusLabel;

  readonly mainTab = signal<MainTab>('integration');
  readonly subTab = signal<IntegrationSubTab>('info');

  setMainTab(tab: MainTab): void {
    this.mainTab.set(tab);
  }

  setSubTab(tab: IntegrationSubTab): void {
    this.subTab.set(tab);
  }

  readonly searchQuery = signal('');
  readonly statusFilter = signal('');
  readonly shipmentFormOpen = signal(false);
  readonly refreshingId = signal<number | null>(null);
  readonly refreshError = signal<string | null>(null);

  readonly shipmentsState = toSignal(
    toObservable(
      computed(() => ({
        query: this.searchQuery().trim(),
        status: this.statusFilter(),
        tick: this.ghnShipmentService.changed(),
      })),
    ).pipe(switchMap(({ query, status }) => toApiState(this.ghnShipmentService.list(query, status)))),
    { initialValue: { data: null, error: null } },
  );

  onSearchInput(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
  }

  onStatusFilterChange(event: Event): void {
    this.statusFilter.set((event.target as HTMLSelectElement).value);
  }

  onShipmentSaved(): void {
    this.shipmentFormOpen.set(false);
  }

  refreshShipment(shipment: GhnShipmentDTO): void {
    this.refreshingId.set(shipment.id);
    this.refreshError.set(null);
    this.ghnShipmentService.refresh(shipment.id).subscribe({
      next: () => {
        this.refreshingId.set(null);
        this.ghnShipmentService.notifyChanged();
      },
      error: (err: HttpErrorResponse) => {
        this.refreshingId.set(null);
        this.refreshError.set(err.error?.error ?? 'Không thể làm mới trạng thái.');
      },
    });
  }
}
