import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SupplierDTO, SupplierListQuery, SupplierPage, SupplierRequest } from './supplier.models';

const BASE_URL = `${environment.apiUrl}/store/suppliers`;

/** How many names the Nhập hàng form's "Tìm nhà cung cấp" dropdown offers at once. */
const SEARCH_PAGE_SIZE = 20;

@Injectable({ providedIn: 'root' })
export class SupplierService {
  constructor(private readonly http: HttpClient) {}

  /** Bumped after create/update/delete/status changes - the list refetches on it, same pattern as PurchaseOrderService.changed. */
  private readonly changedTick = signal(0);
  readonly changed = this.changedTick.asReadonly();
  notifyChanged(): void {
    this.changedTick.update((t) => t + 1);
  }

  /** The Nhập hàng form's supplier box: active suppliers only, matched on mã/tên/điện thoại. */
  search(query?: string): Observable<SupplierPage> {
    const params = new URLSearchParams({ status: 'active', page: '0', size: String(SEARCH_PAGE_SIZE) });
    if (query) {
      params.set('query', query);
    }
    return this.http.get<SupplierPage>(`${BASE_URL}?${params.toString()}`);
  }

  /** The Nhà cung cấp list: every filter its sidebar has, plus the totals row's two sums. */
  list(query: SupplierListQuery): Observable<SupplierPage> {
    const params = new URLSearchParams({ status: query.status });
    const text: [string, string][] = [
      ['query', query.query],
      ['phone', query.phone],
      ['note', query.note],
      ['group', query.group],
    ];
    text.forEach(([key, value]) => {
      if (value.trim()) {
        params.set(key, value.trim());
      }
    });
    const numbers: [string, number | null][] = [
      ['totalFrom', query.totalFrom],
      ['totalTo', query.totalTo],
      ['debtFrom', query.debtFrom],
      ['debtTo', query.debtTo],
    ];
    numbers.forEach(([key, value]) => {
      if (value !== null) {
        params.set(key, String(value));
      }
    });
    if (query.from) {
      params.set('from', query.from);
    }
    if (query.to) {
      params.set('to', query.to);
    }
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    return this.http.get<SupplierPage>(`${BASE_URL}?${params.toString()}`);
  }

  /** "Nhóm nhà cung cấp" options - whatever groups the shop has actually typed on its suppliers. */
  groups(): Observable<{ groups: string[] }> {
    return this.http.get<{ groups: string[] }>(`${BASE_URL}/groups`);
  }

  create(request: SupplierRequest): Observable<{ message: string; supplier: SupplierDTO }> {
    return this.http.post<{ message: string; supplier: SupplierDTO }>(BASE_URL, request);
  }

  update(id: number, request: SupplierRequest): Observable<{ message: string; supplier: SupplierDTO }> {
    return this.http.put<{ message: string; supplier: SupplierDTO }>(`${BASE_URL}/${id}`, request);
  }

  /** "Ngừng hoạt động" and its undo - the supplier keeps its receipts either way. */
  setActive(id: number, active: boolean): Observable<{ message: string; supplier: SupplierDTO }> {
    return this.http.patch<{ message: string; supplier: SupplierDTO }>(`${BASE_URL}/${id}/active`, { active });
  }

  /** "Xóa" - refused with 409 once the supplier appears on a goods receipt; deactivate instead. */
  delete(id: number): Observable<{ message: string; supplierId: number }> {
    return this.http.delete<{ message: string; supplierId: number }>(`${BASE_URL}/${id}`);
  }
}
