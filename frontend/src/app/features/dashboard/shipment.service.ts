import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateShipmentRequest,
  LocationOption,
  PickupAddress,
  RateQuoteRequest,
  SavePickupAddressRequest,
  ShipmentDTO,
  ShippingRate,
} from './shipment.models';

const BASE_URL = `${environment.apiUrl}/store/shipping`;

/**
 * Shipping through Goship, which fronts GHN, GHTK, Viettel Post, J&T, Ninja
 * Van, SPX, BEST and VNPost behind one integration. The path is
 * carrier-neutral because the carrier is now chosen per order, from the
 * live comparison {@link rates} returns.
 */
@Injectable({ providedIn: 'root' })
export class ShipmentService {
  constructor(private readonly http: HttpClient) {}

  /** Bumped after create/refresh - the list route listens to this to refetch, same pattern as PurchaseOrderService.changed. */
  private readonly changedTick = signal(0);
  readonly changed = this.changedTick.asReadonly();
  notifyChanged(): void {
    this.changedTick.update((t) => t + 1);
  }

  /** This store's pickup point. Answers even when unset - the settings form exists to fill it in. */
  pickup(): Observable<PickupAddress> {
    return this.http.get<PickupAddress>(`${BASE_URL}/pickup`);
  }

  savePickup(request: SavePickupAddressRequest): Observable<PickupAddress> {
    return this.http.put<PickupAddress>(`${BASE_URL}/pickup`, request);
  }

  cities(): Observable<{ cities: LocationOption[] }> {
    return this.http.get<{ cities: LocationOption[] }>(`${BASE_URL}/cities`);
  }

  districts(cityId: string): Observable<{ districts: LocationOption[] }> {
    return this.http.get<{ districts: LocationOption[] }>(`${BASE_URL}/districts`, { params: { cityId } });
  }

  wards(districtId: string): Observable<{ wards: LocationOption[] }> {
    return this.http.get<{ wards: LocationOption[] }>(`${BASE_URL}/wards`, { params: { districtId } });
  }

  /**
   * Every carrier's price for one route, cheapest first. POST despite
   * reading nothing: the parcel and the destination are a structured body,
   * and Goship prices from all of it.
   */
  rates(request: RateQuoteRequest): Observable<{ rates: ShippingRate[] }> {
    return this.http.post<{ rates: ShippingRate[] }>(`${BASE_URL}/rates`, request);
  }

  list(query: string, status: string): Observable<{ shipments: ShipmentDTO[] }> {
    const params: Record<string, string> = {};
    if (query) {
      params['query'] = query;
    }
    if (status) {
      params['status'] = status;
    }
    return this.http.get<{ shipments: ShipmentDTO[] }>(`${BASE_URL}/shipments`, { params });
  }

  create(request: CreateShipmentRequest): Observable<{ message: string; shipment: ShipmentDTO }> {
    return this.http.post<{ message: string; shipment: ShipmentDTO }>(`${BASE_URL}/shipments`, request);
  }

  /** Pulls the current state from Goship. The manual fallback whenever a webhook has not arrived - which in the sandbox is always, since it does not fire them. */
  refresh(id: number): Observable<{ shipment: ShipmentDTO }> {
    return this.http.patch<{ shipment: ShipmentDTO }>(`${BASE_URL}/shipments/${id}/refresh`, {});
  }
}
