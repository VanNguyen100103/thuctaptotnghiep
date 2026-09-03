import { convertToParamMap } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { StoreProfileService } from '../../core/store/store-profile.service';
import { ProductForm } from './product-form';
import { ProductAdminService } from './product-admin.service';
import { ProductCategoryService } from './product-category.service';

/**
 * Only covers the variant-generation logic (generateVariants merge-by-key
 * behavior over free-named attribute groups, and the effect that disables
 * the now-per-row sku/slug/price/stockQuantity controls in variant mode) -
 * this component otherwise has no spec, matching this app's usual
 * services/guards-only testing convention; this one bit of logic is
 * non-trivial enough to earn a spec of its own.
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
        {
          provide: ProductAdminService,
          useValue: {
            notifyChanged: vi.fn(),
            changed: () => 0,
            getBrands: () => of({ brands: [] }),
            getLocations: () => of({ locations: [] }),
          },
        },
        { provide: ProductCategoryService, useValue: { list: () => of({ categories: [], total: 0 }) } },
        { provide: AuthService, useValue: { currentUser: () => ({ storeRole: 'OWNER' }) } },
        {
          provide: StoreProfileService,
          useValue: { getCurrentStore: () => of({ id: 1, name: 'Test Store', slug: 'test-store', logoUrl: '', phone: '', address: '' }) },
        },
      ],
    });
    const fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  // Default attributeGroups() is [{name:'Kích cỡ',...}, {name:'Màu sắc',...}] - matches the pre-generalization defaults.
  it('generateVariants() creates the cartesian product across the default Kích cỡ x Màu sắc groups', () => {
    const component = createComponent();
    component.updateAttributeGroupValues(0, ['S', 'M']);
    component.updateAttributeGroupValues(1, ['Đen', 'Trắng']);

    component.generateVariants();

    expect(component.variantModeEnabled()).toBe(true);
    expect(component.variantRows()).toHaveLength(4);
    const keys = component.variantRows().map((r) => `${r.attributeValues['Kích cỡ']}|${r.attributeValues['Màu sắc']}`);
    expect(keys.sort()).toEqual(['M|Đen', 'M|Trắng', 'S|Đen', 'S|Trắng'].sort());
  });

  it('generateVariants() works with a single axis (e.g. a non-fashion "Hương vị" attribute)', () => {
    const component = createComponent();
    component.removeAttributeGroup(1); // drop "Màu sắc", keep just one axis
    component.renameAttributeGroup(0, 'Hương vị');
    component.updateAttributeGroupValues(0, ['Dâu', 'Vani']);

    component.generateVariants();

    expect(component.variantRows()).toHaveLength(2);
    expect(component.variantRows().map((r) => r.attributeValues['Hương vị']).sort()).toEqual(['Dâu', 'Vani']);
  });

  it('addAttributeGroup() supports a 3rd axis, capped at 3', () => {
    const component = createComponent();
    expect(component.attributeGroups()).toHaveLength(2);

    component.addAttributeGroup();
    expect(component.attributeGroups()).toHaveLength(3);
    expect(component.canAddAttributeGroup()).toBe(false);

    component.addAttributeGroup(); // no-op past the cap
    expect(component.attributeGroups()).toHaveLength(3);
  });

  it('generateVariants() re-run preserves manually-edited rows instead of resetting them', () => {
    const component = createComponent();
    component.updateAttributeGroupValues(0, ['S', 'M']);
    component.updateAttributeGroupValues(1, ['Đen']);
    component.generateVariants();

    component.updateVariantRow(0, { price: 999_000, stockQuantity: 5 });

    // re-click "Tạo biến thể" with the same combos (e.g. after tweaking an unrelated field)
    component.generateVariants();

    const row = component.variantRows().find((r) => r.attributeValues['Kích cỡ'] === 'S');
    expect(row?.price).toBe(999_000);
    expect(row?.stockQuantity).toBe(5);
  });

  it('generateVariants() picks up a newly-added chip without disturbing existing rows', () => {
    const component = createComponent();
    component.updateAttributeGroupValues(0, ['S']);
    component.updateAttributeGroupValues(1, ['Đen']);
    component.generateVariants();
    component.updateVariantRow(0, { price: 500_000 });

    component.updateAttributeGroupValues(1, ['Đen', 'Trắng']);
    component.generateVariants();

    expect(component.variantRows()).toHaveLength(2);
    expect(component.variantRows().find((r) => r.attributeValues['Màu sắc'] === 'Đen')?.price).toBe(500_000);
    expect(component.variantRows().find((r) => r.attributeValues['Màu sắc'] === 'Trắng')).toBeTruthy();
  });

  it('removeVariantRow() drops just that row', () => {
    const component = createComponent();
    component.updateAttributeGroupValues(0, ['S']);
    component.updateAttributeGroupValues(1, ['Đen', 'Trắng']);
    component.generateVariants();

    component.removeVariantRow(0);

    expect(component.variantRows()).toHaveLength(1);
  });

  it('cancelVariantMode() clears rows and turns variant mode off', () => {
    const component = createComponent();
    component.updateAttributeGroupValues(0, ['S']);
    component.updateAttributeGroupValues(1, ['Đen']);
    component.generateVariants();

    component.cancelVariantMode();

    expect(component.variantModeEnabled()).toBe(false);
    expect(component.variantRows()).toHaveLength(0);
  });

  it('entering variant mode disables the top-level sku/slug/price/stockQuantity controls', () => {
    const component = createComponent();
    component.updateAttributeGroupValues(0, ['S']);
    component.updateAttributeGroupValues(1, ['Đen']);

    component.generateVariants();
    TestBed.flushEffects();

    expect(component.form.controls.sku.disabled).toBe(true);
    expect(component.form.controls.slug.disabled).toBe(true);
    expect(component.form.controls.price.disabled).toBe(true);
    expect(component.form.controls.stockQuantity.disabled).toBe(true);
  });

  it('cancelling variant mode re-enables those controls', () => {
    const component = createComponent();
    component.updateAttributeGroupValues(0, ['S']);
    component.updateAttributeGroupValues(1, ['Đen']);
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
