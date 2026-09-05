import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, output, signal, viewChild } from '@angular/core';

import { ProductImportRequestOptions, ProductImportResult } from './product-admin.models';
import { ProductAdminService } from './product-admin.service';
import { ActionError, toActionError } from './subscription-error.util';

type YesNo = 'no' | 'yes';
type ErrorOrReplace = 'error' | 'replace';

/**
 * KiotViet's "Nhập hàng hóa từ file dữ liệu" dialog - 5 radio choices, then
 * a native file picker that uploads immediately on selection. The result
 * view (created/updated counts, stop reason, per-row notes) replaces the
 * options view in place rather than closing the modal, since KiotViet's own
 * import can legitimately stop partway through a file.
 */
@Component({
  selector: 'app-product-import-modal',
  standalone: true,
  templateUrl: './product-import-modal.html',
})
export class ProductImportModal {
  private readonly productService = inject(ProductAdminService);

  readonly dismissed = output<void>();

  readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

  readonly duplicateNameChoice = signal<ErrorOrReplace>('error');
  readonly duplicateSkuChoice = signal<ErrorOrReplace>('error');
  readonly updateStockChoice = signal<YesNo>('no');
  readonly updateCostChoice = signal<YesNo>('no');
  readonly updateDescriptionChoice = signal<YesNo>('no');

  readonly uploading = signal(false);
  readonly downloading = signal(false);
  readonly result = signal<ProductImportResult | null>(null);
  readonly error = signal<ActionError | null>(null);

  downloadTemplate(): void {
    this.downloading.set(true);
    this.productService.downloadImportTemplate().subscribe({
      next: (blob: Blob) => {
        this.downloading.set(false);
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'mau-nhap-hang-hoa.xlsx';
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.downloading.set(false),
    });
  }

  openFilePicker(): void {
    this.fileInput().nativeElement.click();
  }

  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    (event.target as HTMLInputElement).value = '';
    if (!file) {
      return;
    }
    this.error.set(null);
    this.uploading.set(true);
    const options: ProductImportRequestOptions = {
      replaceDuplicateName: this.duplicateNameChoice() === 'replace',
      replaceDuplicateSku: this.duplicateSkuChoice() === 'replace',
      updateStock: this.updateStockChoice() === 'yes',
      updateCostPrice: this.updateCostChoice() === 'yes',
      updateDescription: this.updateDescriptionChoice() === 'yes',
    };
    this.productService.importFromFile(file, options).subscribe({
      next: (result: ProductImportResult) => {
        this.uploading.set(false);
        this.result.set(result);
        if (result.createdCount > 0 || result.updatedCount > 0) {
          this.productService.notifyChanged();
        }
      },
      error: (err: HttpErrorResponse) => {
        this.uploading.set(false);
        this.error.set(toActionError(err));
      },
    });
  }

  reset(): void {
    this.result.set(null);
    this.error.set(null);
  }

  close(): void {
    this.dismissed.emit();
  }
}
