import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import { extractErrorMessage } from '../../core/http/api-error';
import { ActionErrorBanner } from './action-error-banner';
import { ChipInput } from './chip-input';
import { ProductImageGallery } from './product-image-gallery';
import { AdminCategory, ProductDTO, ProductImage } from './product-admin.models';
import { ProductAdminService } from './product-admin.service';
import { ProductCategoryService } from './product-category.service';
import { slugify, suggestSku } from './slugify';
import { ActionError, toActionError } from './subscription-error.util';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ChipInput, ProductImageGallery, ActionErrorBanner],
  templateUrl: './product-form.html',
})
export class ProductForm {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly productService = inject(ProductAdminService);
  private readonly categoryService = inject(ProductCategoryService);

  private readonly paramMap = toSignal(this.route.paramMap, { requireSync: true });
  readonly productId = computed(() => {
    const raw = this.paramMap()!.get('productId');
    return raw ? Number(raw) : null;
  });
  readonly isEditMode = computed(() => this.productId() !== null);

  readonly justCreated = signal(this.route.snapshot.queryParamMap.get('created') === 'true');

  readonly loadedProduct = signal<ProductDTO | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  readonly categories = toSignal(
    this.categoryService.list().pipe(catchError(() => of({ categories: [], total: 0 }))),
    { initialValue: { categories: [], total: 0 } },
  );

  readonly sizes = signal<string[]>([]);
  readonly colors = signal<string[]>([]);
  readonly categoryIds = signal<number[]>([]);

  readonly slugTouched = signal(false);
  readonly skuTouched = signal(false);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(200)]],
    slug: ['', Validators.required],
    sku: ['', Validators.required],
    shortDescription: [''],
    description: [''],
    price: [0, [Validators.required, Validators.min(1)]],
    compareAtPrice: [0, Validators.min(0)],
    stockQuantity: [0, Validators.min(0)],
    brand: [''],
    material: [''],
    gender: [''],
    featured: [false],
    active: [true],
  });

  constructor() {
    // Load the existing product in edit mode and patch the form + local signals.
    effect(() => {
      const id = this.productId();
      if (id === null) {
        return;
      }
      this.productService.getById(id).subscribe({
        next: (product) => {
          this.loadedProduct.set(product);
          this.form.patchValue({
            name: product.name,
            slug: product.slug,
            sku: product.sku,
            shortDescription: product.shortDescription ?? '',
            description: product.description ?? '',
            price: product.price,
            compareAtPrice: product.compareAtPrice ?? 0,
            stockQuantity: product.stockQuantity,
            brand: product.brand ?? '',
            material: product.material ?? '',
            gender: product.gender ?? '',
            featured: product.featured,
            active: product.active,
          });
          // Backend silently ignores these on PUT - disable rather than hide,
          // so the owner sees why edits here don't stick.
          this.form.controls.slug.disable();
          this.form.controls.sku.disable();
          this.form.controls.shortDescription.disable();
          this.form.controls.featured.disable();
          this.form.controls.active.disable();
          this.sizes.set([...product.availableSizes]);
          this.colors.set([...product.availableColors]);
          this.categoryIds.set(product.categories.map((c) => c.id));
        },
        error: (err: HttpErrorResponse) => this.loadError.set(extractErrorMessage(err)),
      });
    });

    // Auto-fill slug/sku from name while the user hasn't edited them directly.
    this.form.controls.name.valueChanges.subscribe((name) => {
      if (this.isEditMode()) {
        return;
      }
      if (!this.slugTouched()) {
        this.form.controls.slug.setValue(slugify(name), { emitEvent: false });
      }
      if (!this.skuTouched()) {
        this.form.controls.sku.setValue(suggestSku(name), { emitEvent: false });
      }
    });
  }

  onSlugInput(): void {
    this.slugTouched.set(true);
  }

  onSkuInput(): void {
    this.skuTouched.set(true);
  }

  regenerateSlugAndSku(): void {
    const name = this.form.controls.name.value;
    this.form.controls.slug.setValue(slugify(name));
    this.form.controls.sku.setValue(suggestSku(name));
    this.slugTouched.set(false);
    this.skuTouched.set(false);
  }

  toggleCategory(categoryId: number, checked: boolean): void {
    this.categoryIds.update((ids) =>
      checked ? [...ids, categoryId] : ids.filter((id) => id !== categoryId),
    );
  }

  isCategoryChecked(category: AdminCategory): boolean {
    return this.categoryIds().includes(category.id);
  }

  onImagesChanged(images: ProductImage[]): void {
    this.loadedProduct.update((product) => (product ? { ...product, images } : product));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.actionError.set(null);
    const value = this.form.getRawValue();

    if (this.isEditMode()) {
      const id = this.productId()!;
      this.productService
        .update(id, {
          name: value.name,
          description: value.description || undefined,
          price: value.price,
          compareAtPrice: value.compareAtPrice || undefined,
          stockQuantity: value.stockQuantity,
          brand: value.brand || undefined,
          material: value.material || undefined,
          gender: value.gender || undefined,
          availableSizes: this.sizes(),
          availableColors: this.colors(),
        })
        .subscribe({
          next: () => this.syncCategoriesThenFinish(id),
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            this.actionError.set(toActionError(err));
          },
        });
    } else {
      this.productService
        .create({
          name: value.name,
          slug: value.slug,
          sku: value.sku,
          shortDescription: value.shortDescription || undefined,
          description: value.description || undefined,
          price: value.price,
          compareAtPrice: value.compareAtPrice || undefined,
          stockQuantity: value.stockQuantity,
          active: value.active,
          featured: value.featured,
          availableSizes: this.sizes(),
          availableColors: this.colors(),
          brand: value.brand || undefined,
          material: value.material || undefined,
          gender: value.gender || undefined,
        })
        .subscribe({
          next: (res) => this.syncCategoriesThenFinish(res.product.id),
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            // Likely a duplicate slug/sku DB constraint - no structured field
            // to blame, so just reroll and let the user retry.
            this.regenerateSlugAndSku();
            this.actionError.set(toActionError(err));
          },
        });
    }
  }

  private syncCategoriesThenFinish(productId: number): void {
    this.productService.replaceCategories(productId, this.categoryIds()).subscribe({
      next: () => {
        this.submitting.set(false);
        if (!this.isEditMode()) {
          this.router.navigate(['/dashboard/products', productId, 'edit'], { queryParams: { created: 'true' } });
        } else {
          this.justCreated.set(false);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }
}
