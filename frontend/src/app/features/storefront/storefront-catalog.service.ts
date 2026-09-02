import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Category, ProductDetailResponse, ProductPage, ProductSortBy, SortDirection, Store } from './storefront.models';

@Injectable({ providedIn: 'root' })
export class StorefrontCatalogService {
  constructor(private readonly http: HttpClient) {}

  getStore(storeSlug: string): Observable<Store> {
    return this.http.get<Store>(`${environment.apiUrl}/stores/${storeSlug}`);
  }

  getProducts(
    storeSlug: string,
    page = 0,
    size = 20,
    sortBy: ProductSortBy = 'createdAt',
    sortDirection: SortDirection = 'DESC',
  ): Observable<ProductPage> {
    return this.http.get<ProductPage>(`${environment.apiUrl}/stores/${storeSlug}/products`, {
      params: { page, size, sortBy, sortDirection },
    });
  }

  getProduct(storeSlug: string, productId: number): Observable<ProductDetailResponse> {
    return this.http.get<ProductDetailResponse>(`${environment.apiUrl}/stores/${storeSlug}/products/${productId}`);
  }

  getCategories(storeSlug: string): Observable<Category[]> {
    return this.http.get<Category[]>(`${environment.apiUrl}/stores/${storeSlug}/categories`);
  }
}
