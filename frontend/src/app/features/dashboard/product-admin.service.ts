import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateProductRequest,
  ProductAdminSortBy,
  ProductDTO,
  ProductPage,
  ProductSearchPage,
  ProductStats,
  SortDirection,
  UpdateProductRequest,
} from './product-admin.models';

const BASE_URL = `${environment.apiUrl}/store/products`;

@Injectable({ providedIn: 'root' })
export class ProductAdminService {
  constructor(private readonly http: HttpClient) {}

  list(
    page = 0,
    size = 20,
    sortBy: ProductAdminSortBy = 'createdAt',
    sortDirection: SortDirection = 'DESC',
    active?: boolean,
  ): Observable<ProductPage> {
    const params: Record<string, string | number | boolean> = { page, size, sortBy, sortDirection };
    if (active !== undefined) {
      params['active'] = active;
    }
    return this.http.get<ProductPage>(BASE_URL, { params });
  }

  search(
    query: string,
    page = 0,
    size = 20,
    sortBy: ProductAdminSortBy = 'createdAt',
    sortDirection: SortDirection = 'DESC',
  ): Observable<ProductSearchPage> {
    return this.http.get<ProductSearchPage>(`${BASE_URL}/search`, {
      params: { query, page, size, sortBy, sortDirection },
    });
  }

  getById(productId: number): Observable<ProductDTO> {
    return this.http.get<ProductDTO>(`${BASE_URL}/${productId}`);
  }

  getStats(): Observable<ProductStats> {
    return this.http.get<ProductStats>(`${BASE_URL}/stats`);
  }

  create(request: CreateProductRequest): Observable<{ message: string; product: ProductDTO }> {
    return this.http.post<{ message: string; product: ProductDTO }>(BASE_URL, request);
  }

  update(
    productId: number,
    request: UpdateProductRequest,
  ): Observable<{ message: string; product: ProductDTO }> {
    return this.http.put<{ message: string; product: ProductDTO }>(`${BASE_URL}/${productId}`, request);
  }

  updateStock(productId: number, stockQuantity: number): Observable<{ message: string; productId: number; stockQuantity: number }> {
    return this.http.patch<{ message: string; productId: number; stockQuantity: number }>(
      `${BASE_URL}/${productId}/stock`,
      { stockQuantity },
    );
  }

  updateStatus(productId: number, active: boolean): Observable<{ message: string; productId: number; active: boolean }> {
    return this.http.patch<{ message: string; productId: number; active: boolean }>(
      `${BASE_URL}/${productId}/status`,
      { active },
    );
  }

  delete(productId: number): Observable<{ message: string; productId: number }> {
    return this.http.delete<{ message: string; productId: number }>(`${BASE_URL}/${productId}`);
  }

  replaceCategories(
    productId: number,
    categoryIds: number[],
  ): Observable<{ message: string; productId: number; categories: { id: number; name: string; slug: string }[] }> {
    return this.http.patch<{ message: string; productId: number; categories: { id: number; name: string; slug: string }[] }>(
      `${BASE_URL}/${productId}/categories`,
      { categoryIds },
    );
  }
}
