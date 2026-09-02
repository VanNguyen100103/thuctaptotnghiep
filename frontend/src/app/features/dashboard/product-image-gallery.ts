import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, output, signal } from '@angular/core';

import { ActionErrorBanner } from './action-error-banner';
import { ProductImage } from './product-admin.models';
import { ProductImageService } from './product-image.service';
import { ActionError, toActionError } from './subscription-error.util';

const MAX_FILES = 10;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];

@Component({
  selector: 'app-product-image-gallery',
  standalone: true,
  imports: [ActionErrorBanner],
  templateUrl: './product-image-gallery.html',
})
export class ProductImageGallery {
  private readonly imageService = inject(ProductImageService);

  readonly productId = input.required<number>();
  readonly images = input.required<ProductImage[]>();
  readonly imagesChange = output<ProductImage[]>();

  readonly colorTag = signal('');
  readonly uploading = signal(false);
  readonly validationError = signal<string | null>(null);
  readonly actionError = signal<ActionError | null>(null);

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length === 0) {
      return;
    }

    this.validationError.set(null);
    if (files.length > MAX_FILES) {
      this.validationError.set(`Tối đa ${MAX_FILES} ảnh mỗi lần tải lên.`);
      return;
    }
    const invalid = files.find((f) => !ALLOWED_TYPES.includes(f.type) || f.size > MAX_FILE_SIZE_BYTES);
    if (invalid) {
      this.validationError.set('Chỉ nhận ảnh JPEG/PNG/GIF/WEBP, tối đa 10MB mỗi ảnh.');
      return;
    }

    this.uploading.set(true);
    this.actionError.set(null);
    this.imageService.upload(this.productId(), files, this.colorTag() || undefined).subscribe({
      next: (res) => {
        this.uploading.set(false);
        this.imagesChange.emit(res.product.images);
      },
      error: (err: HttpErrorResponse) => {
        this.uploading.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  deleteImage(imageId: number): void {
    this.actionError.set(null);
    this.imageService.delete(imageId).subscribe({
      next: () => {
        const remaining = this.images().filter((img) => img.id !== imageId);
        this.imagesChange.emit(remaining);
      },
      error: (err: HttpErrorResponse) => this.actionError.set(toActionError(err)),
    });
  }
}
