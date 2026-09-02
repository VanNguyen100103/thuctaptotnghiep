import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AdminCategory } from './product-admin.models';

@Injectable({ providedIn: 'root' })
export class ProductCategoryService {
  constructor(private readonly http: HttpClient) {}

  list(): Observable<{ categories: AdminCategory[]; total: number }> {
    return this.http.get<{ categories: AdminCategory[]; total: number }>(`${environment.apiUrl}/categories`);
  }
}
