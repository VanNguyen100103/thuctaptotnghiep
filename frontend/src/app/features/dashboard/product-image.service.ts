import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ProductDTO } from './product-admin.models';

@Injectable({ providedIn: 'root' })
export class ProductImageService {
  constructor(private readonly http: HttpClient) {}

  upload(productId: number, files: File[], color?: string): Observable<{ message: string; product: ProductDTO }> {
    const formData = new FormData();
    for (const file of files) {
      formData.append('images', file);
    }

    const params: Record<string, string> = {};
    if (color) {
      params['color'] = color;
    }

    return this.http.post<{ message: string; product: ProductDTO }>(
      `${environment.apiUrl}/products/${productId}/images`,
      formData,
      { params },
    );
  }

  delete(imageId: number): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${environment.apiUrl}/products/images/${imageId}`);
  }
}
