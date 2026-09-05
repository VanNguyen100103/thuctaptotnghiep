import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { extractErrorMessage } from '../../core/http/api-error';
import { StoreProfileService } from '../../core/store/store-profile.service';
import { ActionErrorBanner } from './action-error-banner';
import { ALLOWED_IMAGE_TYPES, MAX_FILES, MAX_FILE_SIZE_BYTES, ProductImageGallery } from './product-image-gallery';
import { ProductImageService } from './product-image.service';
import { UnitAttributeSetup } from './unit-attribute-setup';
import { AdminCategory, ProductDTO, ProductImage } from './product-admin.models';
import { ProductAdminService } from './product-admin.service';
import { ProductCategoryService } from './product-category.service';
import { slugify, suggestSku } from './slugify';
import { ActionError, toActionError } from './subscription-error.util';
import { AttributeGroup, UNIT_AXIS_NAME, UnitDef, VariantRowDraft } from './variant-builder.models';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [ReactiveFormsModule, ProductImageGallery, ActionErrorBanner, UnitAttributeSetup],
  templateUrl: './product-form.html',
})
export class ProductForm {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly productService = inject(ProductAdminService);
  private readonly categoryService = inject(ProductCategoryService);
  private readonly authService = inject(AuthService);
  private readonly storeProfileService = inject(StoreProfileService);
  private readonly imageService = inject(ProductImageService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isOwner = computed(() => this.authService.currentUser()?.storeRole === 'OWNER');

  private readonly paramMap = toSignal(this.route.paramMap, { requireSync: true });
  readonly productId = computed(() => {
    const raw = this.paramMap()!.get('productId');
    return raw ? Number(raw) : null;
  });
  readonly isEditMode = computed(() => this.productId() !== null);

  readonly justCreated = signal(this.route.snapshot.queryParamMap.get('created') === 'true');

  readonly activeTab = signal<'info' | 'description' | 'branch'>('info');
  readonly costPriceSectionOpen = signal(true);
  readonly stockSectionOpen = signal(true);
  /** "Vị trí, trọng lượng, kích thước" - expanded by default, matching KiotViet's own form. */
  readonly locationSectionOpen = signal(true);
  /** Custom "Nhóm hàng" combobox (checkbox panel behind a single closed-looking field, like KiotViet's dropdown) - open/closed state. */
  readonly categoryDropdownOpen = signal(false);

  readonly currentStore = toSignal(this.storeProfileService.getCurrentStore(), { initialValue: null });

  /**
   * Images picked before the product exists yet (create mode only) - held as
   * File objects + local object-URL previews, then uploaded right after the
   * product (or, in variant mode, every generated variant row) is actually
   * created, instead of forcing "save first, then add photos".
   */
  readonly stagedImages = signal<File[]>([]);
  readonly stagedImagePreviews = signal<string[]>([]);
  readonly imageValidationError = signal<string | null>(null);
  /** The first staged image renders large as the primary photo; these fill the remaining thumbnail slots (up to 4) alongside images 2-5. */
  readonly emptyImageSlots = computed(() => Array.from({ length: Math.max(0, 4 - Math.max(0, this.stagedImagePreviews().length - 1)) }));

  /** Whether the "Thiết lập đơn vị tính và thuộc tính" popup (UnitAttributeSetup) is open - create mode only. */
  readonly unitSetupModalOpen = signal(false);

  /** Variant generation - create mode only, see generateVariants(). Free-named attribute axes (not hardcoded to color/size) so any industry can define its own; selling units (Hộp/Lốc/Thùng) fold in as an extra synthetic axis, see UNIT_AXIS_NAME. */
  readonly variantModeEnabled = signal(false);
  readonly variantRows = signal<VariantRowDraft[]>([]);
  readonly attributeGroups = signal<AttributeGroup[]>([{ name: '', values: [] }]);
  readonly units = signal<UnitDef[]>([]);
  readonly activeAttributeGroups = computed(() =>
    this.attributeGroups().filter((g) => g.name.trim().length > 0 && g.values.length > 0),
  );
  /** The actual attribute/unit axis names in the generated table (may differ from live attributeGroups()/units() if edited since generating). */
  readonly variantColumnNames = computed(() => Object.keys(this.variantRows()[0]?.attributeValues ?? {}));

  readonly loadedProduct = signal<ProductDTO | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  /** Writable (not toSignal-derived) so quickAddCategory() can refresh it in place after creating one. */
  readonly categories = signal<{ categories: AdminCategory[]; total: number }>({ categories: [], total: 0 });
  private loadCategories(): void {
    this.categoryService
      .list()
      .pipe(catchError(() => of({ categories: [], total: 0 })))
      .subscribe((res) => this.categories.set(res));
  }

  readonly brands = toSignal(
    this.productService.getBrands().pipe(catchError(() => of({ brands: [] }))),
    { initialValue: { brands: [] } },
  );
  readonly locations = toSignal(
    this.productService.getLocations().pipe(catchError(() => of({ locations: [] }))),
    { initialValue: { locations: [] } },
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
    barcode: [''],
    shortDescription: [''],
    description: [''],
    notes: [''],
    price: [0, [Validators.required, Validators.min(1)]],
    compareAtPrice: [0, Validators.min(0)],
    costPrice: [0, Validators.min(0)],
    taxRate: [0, [Validators.min(0), Validators.max(100)]],
    stockQuantity: [0, Validators.min(0)],
    minStockThreshold: [0, Validators.min(0)],
    maxStockThreshold: [0, Validators.min(0)],
    brand: [''],
    material: [''],
    gender: [''],
    location: [''],
    weight: [0, Validators.min(0)],
    weightUnit: ['g'],
    width: [0, Validators.min(0)],
    length: [0, Validators.min(0)],
    height: [0, Validators.min(0)],
    dimensionUnit: ['m'],
    loyaltyPointsEnabled: [true],
    featured: [false],
    active: [true],
  });

  /** Comma-joined names of the checked categories, for the closed combobox's display text - "Chọn nhóm hàng" placeholder when none are checked. */
  readonly selectedCategoryLabel = computed(() => {
    const ids = new Set(this.categoryIds());
    const names = this.categories()
      .categories.filter((c) => ids.has(c.id))
      .map((c) => c.name);
    return names.length > 0 ? names.join(', ') : '';
  });

  constructor() {
    this.loadCategories();

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
            barcode: product.barcode ?? '',
            shortDescription: product.shortDescription ?? '',
            description: product.description ?? '',
            notes: product.notes ?? '',
            price: product.price,
            compareAtPrice: product.compareAtPrice ?? 0,
            costPrice: product.costPrice ?? 0,
            taxRate: product.taxRate ?? 0,
            stockQuantity: product.stockQuantity,
            minStockThreshold: product.minStockThreshold ?? 0,
            maxStockThreshold: product.maxStockThreshold ?? 0,
            brand: product.brand ?? '',
            material: product.material ?? '',
            gender: product.gender ?? '',
            location: product.location ?? '',
            weight: product.weight ?? 0,
            weightUnit: product.weightUnit ?? 'g',
            width: product.width ?? 0,
            length: product.length ?? 0,
            height: product.height ?? 0,
            dimensionUnit: product.dimensionUnit ?? 'm',
            loyaltyPointsEnabled: product.loyaltyPointsEnabled,
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

    // In variant mode, sku/slug/price/stockQuantity are per-row (the variant
    // table below) rather than single top-level values - disable rather than
    // hide, so their `required` validators don't block submit().
    effect(() => {
      const controls = [
        this.form.controls.sku,
        this.form.controls.slug,
        this.form.controls.price,
        this.form.controls.stockQuantity,
      ];
      if (this.variantModeEnabled()) {
        controls.forEach((c) => c.disable());
      } else if (!this.isEditMode()) {
        controls.forEach((c) => c.enable());
      }
    });

    this.destroyRef.onDestroy(() => {
      this.stagedImagePreviews().forEach((url) => URL.revokeObjectURL(url));
    });
  }

  close(): void {
    this.router.navigate(['/dashboard/products']);
  }

  /** "Thêm ảnh" before the product exists yet - stages files locally (previewed via object URLs) instead of requiring a saved product first; actually uploaded once create() succeeds, see finishCreate()/submitVariants(). */
  onImageFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length === 0) {
      return;
    }

    this.imageValidationError.set(null);
    const total = this.stagedImages().length + files.length;
    if (total > MAX_FILES) {
      this.imageValidationError.set(`Tối đa ${MAX_FILES} ảnh.`);
      return;
    }
    const invalid = files.find((f) => !ALLOWED_IMAGE_TYPES.includes(f.type) || f.size > MAX_FILE_SIZE_BYTES);
    if (invalid) {
      this.imageValidationError.set('Chỉ nhận ảnh JPEG/PNG/GIF/WEBP, tối đa 10MB mỗi ảnh.');
      return;
    }

    this.stagedImages.update((current) => [...current, ...files]);
    this.stagedImagePreviews.update((current) => [...current, ...files.map((f) => URL.createObjectURL(f))]);
  }

  removeStagedImage(index: number): void {
    URL.revokeObjectURL(this.stagedImagePreviews()[index]);
    this.stagedImages.update((files) => files.filter((_, i) => i !== index));
    this.stagedImagePreviews.update((urls) => urls.filter((_, i) => i !== index));
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

  clearName(): void {
    this.form.controls.name.setValue('');
    this.form.controls.name.markAsTouched();
  }

  /** After "Lưu & Tạo thêm hàng" - matches KiotViet's stay-on-the-form flow for rapid data entry, instead of navigating to the new product's edit page. */
  private resetFormForAnotherProduct(): void {
    this.form.reset({
      name: '',
      slug: '',
      sku: '',
      barcode: '',
      shortDescription: '',
      description: '',
      price: 0,
      compareAtPrice: 0,
      costPrice: 0,
      taxRate: 0,
      stockQuantity: 0,
      minStockThreshold: 0,
      maxStockThreshold: 0,
      brand: '',
      material: '',
      gender: '',
      location: '',
      weight: 0,
      weightUnit: 'g',
      width: 0,
      length: 0,
      height: 0,
      dimensionUnit: 'm',
      loyaltyPointsEnabled: true,
      featured: false,
      active: true,
    });
    this.slugTouched.set(false);
    this.skuTouched.set(false);
    this.categoryIds.set([]);
    this.sizes.set([]);
    this.colors.set([]);
    this.cancelVariantMode();
    this.stagedImagePreviews().forEach((url) => URL.revokeObjectURL(url));
    this.stagedImages.set([]);
    this.stagedImagePreviews.set([]);
    this.imageValidationError.set(null);
    this.addAnotherPending = false;
    this.justSavedAnother.set(true);
    setTimeout(() => this.justSavedAnother.set(false), 4000);
  }

  /**
   * Cartesian product across active attribute groups plus, when any units
   * are defined, a synthetic UNIT_AXIS_NAME axis - merged by attribute-value
   * key so re-clicking after tweaking a chip/unit doesn't wipe manually-
   * edited rows. Units are iterated outermost (so rows group by unit first,
   * matching KiotViet's own row order - Dầu-Hộp, Vani-Hộp, Dầu-Lốc, ...)
   * while each combo's keys are re-inserted with the unit axis last, so
   * variantColumnNames() (which reads key insertion order) displays "Đơn vị
   * tính" as the last column, matching the reference table's column order.
   */
  generateVariants(): void {
    const otherGroups = this.activeAttributeGroups();
    const unitDefs = this.units();
    const unitGroup: AttributeGroup | null =
      unitDefs.length > 0 ? { name: UNIT_AXIS_NAME, values: unitDefs.map((u) => u.name) } : null;
    const iterationGroups = unitGroup ? [unitGroup, ...otherGroups] : otherGroups;
    if (iterationGroups.length === 0) {
      return;
    }
    const displayOrder = [...otherGroups.map((g) => g.name), ...(unitGroup ? [UNIT_AXIS_NAME] : [])];

    let combos: Record<string, string>[] = [{}];
    for (const group of iterationGroups) {
      combos = combos.flatMap((combo) => group.values.map((value) => ({ ...combo, [group.name]: value })));
    }
    combos = combos.map((combo) => {
      const ordered: Record<string, string> = {};
      for (const columnName of displayOrder) {
        ordered[columnName] = combo[columnName];
      }
      return ordered;
    });

    const keyOf = (attrs: Record<string, string>) => displayOrder.map((n) => attrs[n]).join('|');
    const existing = new Map(this.variantRows().map((r) => [keyOf(r.attributeValues), r]));
    const name = this.form.controls.name.value;
    const price = this.form.controls.price.value || 0;
    const costPrice = this.form.controls.costPrice.value || 0;
    const unitByName = new Map(unitDefs.map((u) => [u.name, u]));

    this.variantRows.set(
      combos.map((attributeValues) => {
        const existingRow = existing.get(keyOf(attributeValues));
        if (existingRow) {
          return existingRow;
        }
        const unitDef = unitByName.get(attributeValues[UNIT_AXIS_NAME] ?? '');
        const conversionFactor = unitDef?.conversionFactor ?? 1;
        const suffix = displayOrder.map((n) => attributeValues[n]).join(' ');
        return {
          attributeValues,
          sku: suggestSku(`${name} ${suffix}`),
          barcode: '',
          price: unitDef ? unitDef.price : price,
          costPrice: Math.round(costPrice * conversionFactor),
          stockQuantity: 0,
          active: unitDef ? unitDef.sellDirectly : true,
        };
      }),
    );
    this.variantModeEnabled.set(true);
  }

  updateVariantRow(index: number, patch: Partial<VariantRowDraft>): void {
    this.variantRows.update((rows) => rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  removeVariantRow(index: number): void {
    this.variantRows.update((rows) => rows.filter((_, i) => i !== index));
  }

  /** Full reset of the units+attributes builder - used by resetFormForAnotherProduct() after "Lưu & Tạo thêm hàng". */
  cancelVariantMode(): void {
    this.variantRows.set([]);
    this.variantModeEnabled.set(false);
    this.attributeGroups.set([{ name: '', values: [] }]);
    this.units.set([]);
  }

  toggleCategory(categoryId: number, checked: boolean): void {
    this.categoryIds.update((ids) =>
      checked ? [...ids, categoryId] : ids.filter((id) => id !== categoryId),
    );
  }

  isCategoryChecked(category: AdminCategory): boolean {
    return this.categoryIds().includes(category.id);
  }

  toggleCategoryDropdown(): void {
    this.categoryDropdownOpen.update((open) => !open);
  }

  closeCategoryDropdown(): void {
    this.categoryDropdownOpen.set(false);
  }

  /** Closes the "Nhóm hàng" combobox on any outside click; the panel itself stops propagation so clicks inside it don't reach here. */
  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.categoryDropdownOpen()) {
      this.closeCategoryDropdown();
    }
  }

  /** "Tạo mới" next to Thương hiệu/Vị trí - these are plain free-text columns (no master list to manage), so "creating new" just means clearing the field so the datalist doesn't get in the way of typing one. */
  clearAndFocus(controlName: 'brand' | 'location', input: HTMLInputElement): void {
    this.form.controls[controlName].setValue('');
    input.focus();
  }

  /** "Tạo mới" next to Nhóm hàng - a lightweight prompt rather than a full modal, matching the low-friction quick-add KiotViet offers inline. Newly created category is auto-checked. */
  quickAddCategory(): void {
    const name = window.prompt('Tên nhóm hàng mới:');
    if (!name || !name.trim()) {
      return;
    }
    const trimmed = name.trim();
    this.categoryService.create(trimmed, slugify(trimmed)).subscribe({
      next: (res) => {
        this.categories.update((current) => ({
          categories: [...current.categories, res.category],
          total: current.total + 1,
        }));
        this.toggleCategory(res.category.id, true);
      },
      error: (err: HttpErrorResponse) => this.actionError.set(toActionError(err)),
    });
  }

  onImagesChanged(images: ProductImage[]): void {
    this.loadedProduct.update((product) => (product ? { ...product, images } : product));
  }

  /** Set right before create succeeds when the user clicked "Lưu & Tạo thêm hàng" - see syncCategoriesThenFinish(). */
  private addAnotherPending = false;
  readonly justSavedAnother = signal(false);

  submit(addAnother = false): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.addAnotherPending = addAnother && !this.isEditMode();

    if (!this.isEditMode() && this.variantModeEnabled() && this.variantRows().length > 0) {
      this.submitVariants();
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
          barcode: value.barcode || undefined,
          description: value.description || undefined,
          notes: value.notes || undefined,
          price: value.price,
          compareAtPrice: value.compareAtPrice || undefined,
          costPrice: value.costPrice || undefined,
          taxRate: this.isOwner() ? value.taxRate || undefined : undefined,
          stockQuantity: value.stockQuantity,
          minStockThreshold: value.minStockThreshold || undefined,
          maxStockThreshold: value.maxStockThreshold || undefined,
          brand: value.brand || undefined,
          material: value.material || undefined,
          gender: value.gender || undefined,
          location: value.location || undefined,
          weight: value.weight || undefined,
          weightUnit: value.weightUnit || undefined,
          width: value.width || undefined,
          length: value.length || undefined,
          height: value.height || undefined,
          dimensionUnit: value.dimensionUnit || undefined,
          loyaltyPointsEnabled: value.loyaltyPointsEnabled,
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
          barcode: value.barcode || undefined,
          shortDescription: value.shortDescription || undefined,
          description: value.description || undefined,
          notes: value.notes || undefined,
          price: value.price,
          compareAtPrice: value.compareAtPrice || undefined,
          costPrice: value.costPrice || undefined,
          taxRate: this.isOwner() ? value.taxRate || undefined : undefined,
          stockQuantity: value.stockQuantity,
          minStockThreshold: value.minStockThreshold || undefined,
          maxStockThreshold: value.maxStockThreshold || undefined,
          active: value.active,
          featured: value.featured,
          availableSizes: this.sizes(),
          availableColors: this.colors(),
          brand: value.brand || undefined,
          material: value.material || undefined,
          gender: value.gender || undefined,
          location: value.location || undefined,
          weight: value.weight || undefined,
          weightUnit: value.weightUnit || undefined,
          width: value.width || undefined,
          length: value.length || undefined,
          height: value.height || undefined,
          dimensionUnit: value.dimensionUnit || undefined,
          loyaltyPointsEnabled: value.loyaltyPointsEnabled,
        })
        .subscribe({
          next: (res) => this.uploadStagedImagesThen(res.product.id, () => this.syncCategoriesThenFinish(res.product.id)),
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

  /** Images staged before the product existed (see onImageFilesSelected()) get uploaded now that a real product id exists - a failed upload doesn't block the rest of the save flow, since the product itself is already created. */
  private uploadStagedImagesThen(productId: number, next: () => void): void {
    if (this.stagedImages().length === 0) {
      next();
      return;
    }
    this.imageService.upload(productId, this.stagedImages()).subscribe({
      next,
      error: (err: HttpErrorResponse) => {
        this.actionError.set(toActionError(err));
        next();
      },
    });
  }

  private submitVariants(): void {
    this.submitting.set(true);
    this.actionError.set(null);
    const value = this.form.getRawValue();
    // Derive from the rows actually in the table (not activeAttributeGroups()
    // live state), in case a chip was edited after the table was generated.
    const attributeOrder = Object.keys(this.variantRows()[0]?.attributeValues ?? {});

    this.productService
      .createVariants({
        name: value.name,
        shortDescription: value.shortDescription || undefined,
        description: value.description || undefined,
        categoryIds: this.categoryIds(),
        brand: value.brand || undefined,
        material: value.material || undefined,
        gender: value.gender || undefined,
        location: value.location || undefined,
        weight: value.weight || undefined,
        weightUnit: value.weightUnit || undefined,
        width: value.width || undefined,
        length: value.length || undefined,
        height: value.height || undefined,
        dimensionUnit: value.dimensionUnit || undefined,
        loyaltyPointsEnabled: value.loyaltyPointsEnabled,
        attributeOrder,
        compareAtPrice: value.compareAtPrice || undefined,
        taxRate: this.isOwner() ? value.taxRate || undefined : undefined,
        minStockThreshold: value.minStockThreshold || undefined,
        maxStockThreshold: value.maxStockThreshold || undefined,
        active: value.active,
        featured: value.featured,
        variants: this.variantRows().map((r) => ({
          attributeValues: r.attributeValues,
          sku: r.sku,
          barcode: r.barcode || undefined,
          price: r.price,
          costPrice: r.costPrice || undefined,
          stockQuantity: r.stockQuantity,
          active: r.active,
        })),
      })
      .subscribe({
        next: (res) => {
          const finish = () => {
            this.submitting.set(false);
            this.productService.notifyChanged();
            this.router.navigate(['/dashboard/products']);
          };
          if (this.stagedImages().length === 0) {
            finish();
            return;
          }
          // Same staged photos apply to every generated unit/attribute row.
          forkJoin(res.products.map((p) => this.imageService.upload(p.id, this.stagedImages()))).subscribe({
            next: finish,
            error: (err: HttpErrorResponse) => {
              this.actionError.set(toActionError(err));
              finish();
            },
          });
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.actionError.set(toActionError(err));
        },
      });
  }

  private syncCategoriesThenFinish(productId: number): void {
    this.productService.replaceCategories(productId, this.categoryIds()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.productService.notifyChanged();
        if (this.addAnotherPending) {
          this.resetFormForAnotherProduct();
        } else if (!this.isEditMode()) {
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
