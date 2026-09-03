import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateProductRequest,
  CreateProductVariantsRequest,
  CreateProductVariantsResponse,
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

  /** Bumped whenever a product is created/updated/deleted - product-list listens to this to refetch, since the create/edit modal is now a sibling route rendered inside product-list's own router-outlet, not the same component instance. */
  private readonly changedTick = signal(0);
  readonly changed = this.changedTick.asReadonly();
  notifyChanged(): void {
    this.changedTick.update((t) => t + 1);
  }

  list(
    page = 0,
    size = 20,
    sortBy: ProductAdminSortBy = 'createdAt',
    sortDirection: SortDirection = 'DESC',
    active?: boolean,
    categoryId?: number,
    inStock?: boolean,
  ): Observable<ProductPage> {
    const params: Record<string, string | number | boolean> = { page, size, sortBy, sortDirection };
    if (active !== undefined) {
      params['active'] = active;
    }
    if (categoryId !== undefined) {
      params['categoryId'] = categoryId;
    }
    if (inStock !== undefined) {
      params['inStock'] = inStock;
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

  /** Sibling products sharing this product's variantGroupId - used by the POS "Đơn vị tính" dropdown to switch a cart line between units. */
  getUnitSiblings(productId: number): Observable<{ products: ProductDTO[] }> {
    return this.http.get<{ products: ProductDTO[] }>(`${BASE_URL}/${productId}/unit-siblings`);
  }

  getStats(): Observable<ProductStats> {
    return this.http.get<ProductStats>(`${BASE_URL}/stats`);
  }

  /** Distinct brand values already used in this store, for the "Thương hiệu" autocomplete. */
  getBrands(): Observable<{ brands: string[] }> {
    return this.http.get<{ brands: string[] }>(`${BASE_URL}/brands`);
  }

  /** Distinct "Vị trí" values already used in this store, for the location autocomplete. */
  getLocations(): Observable<{ locations: string[] }> {
    return this.http.get<{ locations: string[] }>(`${BASE_URL}/locations`);
  }

  create(request: CreateProductRequest): Observable<{ message: string; product: ProductDTO }> {
    return this.http.post<{ message: string; product: ProductDTO }>(BASE_URL, request);
  }

  createVariants(request: CreateProductVariantsRequest): Observable<CreateProductVariantsResponse> {
    return this.http.post<CreateProductVariantsResponse>(`${BASE_URL}/variants`, request);
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
