import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { toApiState } from './api-state.util';
import { INTEGRATED_CARRIERS } from './delivery-partner.models';
import { ShipmentFormModal } from './shipment-form-modal';
import { LocationOption, PickupAddress, ShipmentDTO, shipmentStatusLabel } from './shipment.models';
import { ShipmentService } from './shipment.service';

type MainTab = 'integration' | 'other';
type IntegrationSubTab = 'info' | 'shipping-history' | 'reconciliation-history';

/**
 * "Đối tác giao hàng" page, laid out like KiotViet's own: an Integration tab
 * (carrier overview + shipping/reconciliation history) and an "Other" tab
 * for manually-added partners. Nothing here has a backend counterpart yet
 * EXCEPT the shipping-history sub-tab, which books and tracks real shipments
 * through Goship - one integration fronting every carrier listed above it.
 * Every other control that would need a backend stays disabled with a
 * "Sắp ra mắt" tooltip.
 */
@Component({
  selector: 'app-delivery-partners',
  standalone: true,
  imports: [VndCurrencyPipe, ShipmentFormModal],
  templateUrl: './delivery-partners.html',
})
export class DeliveryPartners {
  private readonly shipmentService = inject(ShipmentService);

  readonly carriers = INTEGRATED_CARRIERS;
  readonly shipmentStatusLabel = shipmentStatusLabel;

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
        tick: this.shipmentService.changed(),
      })),
    ).pipe(switchMap(({ query, status }) => toApiState(this.shipmentService.list(query, status)))),
    { initialValue: { data: null, error: null } },
  );

  /**
   * The status filter's options are learned from the shipments that come
   * back, not from a hardcoded table. Goship owns the status list and grows
   * it without us - and it sends a Vietnamese label with every code, so
   * there is nothing here worth duplicating and letting drift.
   *
   * Codes accumulate and are never dropped: filtering to one status would
   * otherwise shrink the list of statuses to that single option, leaving no
   * way back.
   */
  private readonly seenStatuses = signal<Map<number, string>>(new Map());

  readonly statusOptions = computed(() =>
    [...this.seenStatuses().entries()].sort((a, b) => a[0] - b[0]),
  );

  private readonly learnStatuses = effect(() => {
    const shipments = this.shipmentsState().data?.shipments;
    if (!shipments?.length) {
      return;
    }
    const next = new Map(this.seenStatuses());
    let added = false;
    for (const shipment of shipments) {
      if (shipment.statusCode !== undefined && !next.has(shipment.statusCode)) {
        next.set(shipment.statusCode, shipmentStatusLabel(shipment));
        added = true;
      }
    }
    if (added) {
      this.seenStatuses.set(next);
    }
  });

  // ---- Điểm lấy hàng ----
  // Set per store, because every shop on the platform ships from its own
  // counter. Nothing quotes or books until it is filled in, so this sits on
  // the first tab rather than being buried.

  readonly pickup = signal<PickupAddress | null>(null);
  readonly pickupCities = signal<LocationOption[]>([]);
  readonly pickupDistricts = signal<LocationOption[]>([]);
  readonly pickupWards = signal<LocationOption[]>([]);
  readonly pickupCityId = signal('');
  readonly pickupDistrictId = signal('');
  readonly pickupWardId = signal('');
  readonly pickupSaving = signal(false);
  readonly pickupError = signal<string | null>(null);
  readonly pickupSaved = signal(false);

  readonly pickupComplete = computed(() => !!(this.pickupCityId() && this.pickupDistrictId() && this.pickupWardId()));

  private readonly loadPickup = effect(() => {
    if (this.mainTab() !== 'integration' || this.subTab() !== 'info' || this.pickup() !== null) {
      return;
    }
    untracked(() => this.fetchPickup());
  });

  private fetchPickup(): void {
    this.shipmentService.pickup().subscribe({
      next: (pickup) => {
        this.pickup.set(pickup);
        this.pickupCityId.set(pickup.cityId ?? '');
        this.pickupDistrictId.set(pickup.districtId ?? '');
        this.pickupWardId.set(pickup.wardId ?? '');
        this.loadPickupCities(pickup);
      },
      error: (err: HttpErrorResponse) => this.pickupError.set(err.error?.error ?? 'Không tải được điểm lấy hàng.'),
    });
  }

  /** Reloads the lists a saved selection sits in, so a returning owner sees names rather than blank dropdowns. */
  private loadPickupCities(pickup: PickupAddress): void {
    this.shipmentService.cities().subscribe({
      next: (res) => {
        this.pickupCities.set(res.cities);
        if (pickup.cityId) {
          this.shipmentService.districts(pickup.cityId).subscribe({
            next: (d) => {
              this.pickupDistricts.set(d.districts);
              if (pickup.districtId) {
                this.shipmentService
                  .wards(pickup.districtId)
                  .subscribe({ next: (w) => this.pickupWards.set(w.wards), error: () => {} });
              }
            },
            error: () => {},
          });
        }
      },
      error: (err: HttpErrorResponse) => this.pickupError.set(err.error?.error ?? 'Không tải được danh sách tỉnh thành.'),
    });
  }

  onPickupCityChange(event: Event): void {
    const cityId = (event.target as HTMLSelectElement).value;
    this.pickupCityId.set(cityId);
    this.pickupDistrictId.set('');
    this.pickupWardId.set('');
    this.pickupDistricts.set([]);
    this.pickupWards.set([]);
    this.pickupSaved.set(false);
    if (!cityId) {
      return;
    }
    this.shipmentService.districts(cityId).subscribe({
      next: (res) => this.pickupDistricts.set(res.districts),
      error: (err: HttpErrorResponse) => this.pickupError.set(err.error?.error ?? 'Không tải được quận/huyện.'),
    });
  }

  onPickupDistrictChange(event: Event): void {
    const districtId = (event.target as HTMLSelectElement).value;
    this.pickupDistrictId.set(districtId);
    this.pickupWardId.set('');
    this.pickupWards.set([]);
    this.pickupSaved.set(false);
    if (!districtId) {
      return;
    }
    this.shipmentService.wards(districtId).subscribe({
      next: (res) => this.pickupWards.set(res.wards),
      error: (err: HttpErrorResponse) => this.pickupError.set(err.error?.error ?? 'Không tải được phường/xã.'),
    });
  }

  onPickupWardChange(event: Event): void {
    this.pickupWardId.set((event.target as HTMLSelectElement).value);
    this.pickupSaved.set(false);
  }

  savePickup(): void {
    if (!this.pickupComplete()) {
      return;
    }
    this.pickupSaving.set(true);
    this.pickupError.set(null);
    this.pickupSaved.set(false);
    this.shipmentService
      .savePickup({ cityId: this.pickupCityId(), districtId: this.pickupDistrictId(), wardId: this.pickupWardId() })
      .subscribe({
        next: (saved) => {
          this.pickupSaving.set(false);
          this.pickup.set(saved);
          this.pickupSaved.set(true);
        },
        error: (err: HttpErrorResponse) => {
          this.pickupSaving.set(false);
          this.pickupError.set(err.error?.error ?? 'Không lưu được điểm lấy hàng.');
        },
      });
  }

  onSearchInput(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
  }

  onStatusFilterChange(event: Event): void {
    this.statusFilter.set((event.target as HTMLSelectElement).value);
  }

  onShipmentSaved(): void {
    this.shipmentFormOpen.set(false);
  }

  refreshShipment(shipment: ShipmentDTO): void {
    this.refreshingId.set(shipment.id);
    this.refreshError.set(null);
    this.shipmentService.refresh(shipment.id).subscribe({
      next: () => {
        this.refreshingId.set(null);
        this.shipmentService.notifyChanged();
      },
      error: (err: HttpErrorResponse) => {
        this.refreshingId.set(null);
        this.refreshError.set(err.error?.error ?? 'Không thể làm mới trạng thái.');
      },
    });
  }
}
