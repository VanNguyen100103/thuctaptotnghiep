import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CreateGhnShipmentRequest, GhnLocationOption, GhnShipmentDTO } from './ghn-shipment.models';

const BASE_URL = `${environment.apiUrl}/store/ghn`;

@Injectable({ providedIn: 'root' })
export class GhnShipmentService {
  constructor(private readonly http: HttpClient) {}

  /** Bumped after create/refresh - the list route listens to this to refetch, same pattern as PurchaseOrderService.changed. */
  private readonly changedTick = signal(0);
  readonly changed = this.changedTick.asReadonly();
  notifyChanged(): void {
    this.changedTick.update((t) => t + 1);
  }

  provinces(): Observable<{ provinces: GhnLocationOption[] }> {
    return this.http.get<{ provinces: GhnLocationOption[] }>(`${BASE_URL}/provinces`);
  }

  districts(provinceId: string): Observable<{ districts: GhnLocationOption[] }> {
    return this.http.get<{ districts: GhnLocationOption[] }>(`${BASE_URL}/districts`, { params: { provinceId } });
  }

  wards(districtId: string): Observable<{ wards: GhnLocationOption[] }> {
    return this.http.get<{ wards: GhnLocationOption[] }>(`${BASE_URL}/wards`, { params: { districtId } });
  }

  list(query: string, status: string): Observable<{ shipments: GhnShipmentDTO[] }> {
    const params: Record<string, string> = {};
    if (query) {
      params['query'] = query;
    }
    if (status) {
      params['status'] = status;
    }
    return this.http.get<{ shipments: GhnShipmentDTO[] }>(`${BASE_URL}/shipments`, { params });
  }

  create(request: CreateGhnShipmentRequest): Observable<{ message: string; shipment: GhnShipmentDTO }> {
    return this.http.post<{ message: string; shipment: GhnShipmentDTO }>(`${BASE_URL}/shipments`, request);
  }

  refresh(id: number): Observable<{ shipment: GhnShipmentDTO }> {
    return this.http.patch<{ shipment: GhnShipmentDTO }>(`${BASE_URL}/shipments/${id}/refresh`, {});
  }
}
