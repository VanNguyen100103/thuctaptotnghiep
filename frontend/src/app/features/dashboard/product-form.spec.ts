import { convertToParamMap } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { ProductForm } from './product-form';
import { ProductAdminService } from './product-admin.service';
import { ProductCategoryService } from './product-category.service';

/**
 * Only covers the Color x Size variant-generation logic (generateVariants
 * merge-by-key behavior, and the effect that disables the now-per-row
 * sku/slug/price/stockQuantity controls in variant mode) - this component
 * otherwise has no spec, matching this app's usual services/guards-only
 * testing convention; this one bit of logic is non-trivial enough to earn
 * a spec of its own.
 */
describe('ProductForm variant generation', () => {
  function createComponent() {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({})),
            snapshot: { paramMap: convertToParamMap({}), queryParamMap: convertToParamMap({}) },
          },
        },
        { provide: Router, useValue: { navigate: vi.fn(), url: '/dashboard/products/new' } },
        { provide: ProductAdminService, useValue: { notifyChanged: vi.fn(), changed: () => 0 } },
        { provide: ProductCategoryService, useValue: { list: () => of({ categories: [], total: 0 }) } },
        { provide: AuthService, useValue: { currentUser: () => ({ storeRole: 'OWNER' }) } },
      ],
    });
    const fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('generateVariants() creates the cartesian product of colors x sizes', () => {
    const component = createComponent();
    component.colors.set(['Đen', 'Trắng']);
    component.sizes.set(['S', 'M']);

    component.generateVariants();

    expect(component.variantModeEnabled()).toBe(true);
    expect(component.variantRows()).toHaveLength(4);
    expect(component.variantRows().map((r) => `${r.color}|${r.size}`).sort()).toEqual(
      ['Đen|S', 'Đen|M', 'Trắng|S', 'Trắng|M'].sort(),
    );
  });

  it('generateVariants() re-run preserves manually-edited rows instead of resetting them', () => {
    const component = createComponent();
    component.colors.set(['Đen']);
    component.sizes.set(['S', 'M']);
    component.generateVariants();

    component.updateVariantRow(0, { price: 999_000, stockQuantity: 5 });

    // re-click "Tạo biến thể" with the same combos (e.g. after tweaking an unrelated field)
    component.generateVariants();

    const row = component.variantRows().find((r) => r.color === 'Đen' && r.size === 'S');
    expect(row?.price).toBe(999_000);
    expect(row?.stockQuantity).toBe(5);
  });

  it('generateVariants() picks up a newly-added chip without disturbing existing rows', () => {
    const component = createComponent();
    component.colors.set(['Đen']);
    component.sizes.set(['S']);
    component.generateVariants();
    component.updateVariantRow(0, { price: 500_000 });

    component.colors.set(['Đen', 'Trắng']);
    component.generateVariants();

    expect(component.variantRows()).toHaveLength(2);
    expect(component.variantRows().find((r) => r.color === 'Đen')?.price).toBe(500_000);
    expect(component.variantRows().find((r) => r.color === 'Trắng')).toBeTruthy();
  });

  it('removeVariantRow() drops just that row', () => {
    const component = createComponent();
    component.colors.set(['Đen', 'Trắng']);
    component.sizes.set(['S']);
    component.generateVariants();

    component.removeVariantRow(0);

    expect(component.variantRows()).toHaveLength(1);
  });

  it('cancelVariantMode() clears rows and turns variant mode off', () => {
    const component = createComponent();
    component.colors.set(['Đen']);
    component.sizes.set(['S']);
    component.generateVariants();

    component.cancelVariantMode();

    expect(component.variantModeEnabled()).toBe(false);
    expect(component.variantRows()).toHaveLength(0);
  });

  it('entering variant mode disables the top-level sku/slug/price/stockQuantity controls', () => {
    const component = createComponent();
    component.colors.set(['Đen']);
    component.sizes.set(['S']);

    component.generateVariants();
    TestBed.flushEffects();

    expect(component.form.controls.sku.disabled).toBe(true);
    expect(component.form.controls.slug.disabled).toBe(true);
    expect(component.form.controls.price.disabled).toBe(true);
    expect(component.form.controls.stockQuantity.disabled).toBe(true);
  });

  it('cancelling variant mode re-enables those controls', () => {
    const component = createComponent();
    component.colors.set(['Đen']);
    component.sizes.set(['S']);
    component.generateVariants();
    TestBed.flushEffects();

    component.cancelVariantMode();
    TestBed.flushEffects();

    expect(component.form.controls.sku.disabled).toBe(false);
    expect(component.form.controls.slug.disabled).toBe(false);
    expect(component.form.controls.price.disabled).toBe(false);
    expect(component.form.controls.stockQuantity.disabled).toBe(false);
  });
});
