import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  TaxActivityOption,
  TaxDeclarationDetail,
  TaxDeclarationRow,
  TaxProfileDTO,
  TaxProfileRequest,
  TaxYearSummary,
} from './tax.models';

const BASE_URL = `${environment.apiUrl}/store/tax`;

@Injectable({ providedIn: 'root' })
export class TaxService {
  private readonly http = inject(HttpClient);

  /**
   * Bumped whenever something the declaration screens read has changed -
   * a return marked nộp, or Thiết lập saved. Same pattern as
   * SupplierService.changed.
   *
   * Thiết lập counts because it owns the rate pair and the period type: save
   * a different ngành nghề and every figure on the 01/CNKD list is wrong
   * until it refetches.
   */
  private readonly changedTick = signal(0);
  readonly changed = this.changedTick.asReadonly();
  notifyChanged(): void {
    this.changedTick.update((t) => t + 1);
  }

  /** The whole 01/CNKD screen for one year: cards, rows, year picker. */
  declarations(year: number): Observable<TaxYearSummary> {
    return this.http.get<TaxYearSummary>(`${BASE_URL}/declarations?year=${year}`);
  }

  /** "Xem chi tiết" on one period. */
  declarationDetail(year: number, periodNumber: number): Observable<TaxDeclarationDetail> {
    return this.http.get<TaxDeclarationDetail>(`${BASE_URL}/declarations/${year}/${periodNumber}`);
  }

  /** The status dropdown on the list: "Đã nộp" and its undo. */
  setSubmitted(
    year: number,
    periodNumber: number,
    submitted: boolean,
  ): Observable<{ message: string; declaration: TaxDeclarationRow }> {
    return this.http
      .patch<{ message: string; declaration: TaxDeclarationRow }>(
        `${BASE_URL}/declarations/${year}/${periodNumber}/status`,
        { submitted },
      )
      .pipe(tap(() => this.notifyChanged()));
  }

  profile(): Observable<TaxProfileDTO> {
    return this.http.get<TaxProfileDTO>(`${BASE_URL}/profile`);
  }

  saveProfile(request: TaxProfileRequest): Observable<{ message: string; profile: TaxProfileDTO }> {
    return this.http
      .put<{ message: string; profile: TaxProfileDTO }>(`${BASE_URL}/profile`, request)
      .pipe(tap(() => this.notifyChanged()));
  }

  /** Ngành nghề options with their rates - the law's numbers come from the server, never hard-coded in a template. */
  activities(): Observable<{ activities: TaxActivityOption[] }> {
    return this.http.get<{ activities: TaxActivityOption[] }>(`${BASE_URL}/activities`);
  }
}
