import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, of, switchMap } from 'rxjs';

import { extractErrorMessage } from '../../core/http/api-error';
import { ApiState } from './dashboard.models';

export function toApiState<T>(source: Observable<T>): Observable<ApiState<T>> {
  return source.pipe(
    switchMap((data) => of<ApiState<T>>({ data, error: null })),
    catchError((err: HttpErrorResponse) => of<ApiState<T>>({ data: null, error: extractErrorMessage(err) })),
  );
}

export const INITIAL_API_STATE: ApiState<never> = { data: null, error: null };
