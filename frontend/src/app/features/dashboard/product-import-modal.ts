import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap, takeWhile, timer } from 'rxjs';

import { ProductImageImportProgress, ProductImportRequestOptions, ProductImportResult } from './product-admin.models';
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
  private readonly destroyRef = inject(DestroyRef);

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
  /** Set only while/after a sheet's image links are being fetched into Cloudinary in the background. */
  readonly imageProgress = signal<ProductImageImportProgress | null>(null);

  constructor() {
    // An import outlives this dialog, so reopening it during one has to show
    // that import rather than an empty options form.
    this.productService
      .getImportProgress()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (progress) => {
          if (progress.running) {
            this.result.set(progress);
            this.pollImportProgress();
          }
        },
        error: () => {},
      });
  }

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
        // The upload only hands the file over; the sheet is read server-side.
        if (result.running) {
          this.pollImportProgress();
        } else {
          this.onImportFinished(result);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.uploading.set(false);
        this.error.set(toActionError(err));
      },
    });
  }

  /**
   * Follows the server-side parse until it reports running: false. A real
   * export takes tens of minutes, so this also runs on open (see the
   * constructor) - the dialog is meant to be closed and reopened while an
   * import is still going.
   */
  private pollImportProgress(): void {
    timer(1500, 1500)
      .pipe(
        switchMap(() => this.productService.getImportProgress()),
        takeWhile((progress) => progress.running, true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (progress) => {
          this.result.set(progress);
          if (!progress.running) {
            this.onImportFinished(progress);
          }
        },
        // A dropped poll is not worth an error banner over an import that is
        // still running server-side - the last known counts stay on screen.
        error: () => {},
      });
  }

  private onImportFinished(result: ProductImportResult): void {
    if (result.createdCount > 0 || result.updatedCount > 0) {
      this.productService.notifyChanged();
    }
    if (result.queuedImageCount > 0) {
      this.pollImageProgress();
    }
  }

  /**
   * Second phase: once the rows are in, the picture links go to their own
   * upload pool, tracked separately from the row parse. takeWhile's
   * inclusive flag keeps the final (finished) snapshot before completing, so
   * the last counts stay on screen.
   */
  private pollImageProgress(): void {
    timer(0, 2000)
      .pipe(
        switchMap(() => this.productService.getImageImportProgress()),
        takeWhile((progress) => progress.running, true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (progress) => {
          this.imageProgress.set(progress);
          if (!progress.running && progress.uploaded > 0) {
            // Thumbnails only exist once the upload lands, so the list behind
            // the dialog has to refetch to show them.
            this.productService.notifyChanged();
          }
        },
        // A failed poll is not worth an error banner over an import that
        // already succeeded - the upload keeps running server-side.
        error: () => this.imageProgress.set(null),
      });
  }

  reset(): void {
    this.result.set(null);
    this.error.set(null);
    this.imageProgress.set(null);
  }

  close(): void {
    this.dismissed.emit();
  }
}
