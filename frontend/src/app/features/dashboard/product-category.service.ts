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

  /** Quick-add from the product form's "Tạo mới" link - name only, slug auto-derived. */
  create(name: string, slug: string): Observable<{ message: string; category: AdminCategory }> {
    const body = new FormData();
    body.set('name', name);
    body.set('slug', slug);
    return this.http.post<{ message: string; category: AdminCategory }>(`${environment.apiUrl}/categories`, body);
  }
}
