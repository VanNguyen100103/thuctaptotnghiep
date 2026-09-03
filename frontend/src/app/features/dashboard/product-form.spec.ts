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
 * behavior over free-named attribute groups plus the KiotViet-style selling
 * "units" axis, and the effect that disables the now-per-row
 * sku/slug/price/stockQuantity controls in variant mode) - this component
 * otherwise has no spec, matching this app's usual services/guards-only
 * testing convention; this bit of logic is non-trivial enough to earn a spec
 * of its own. Attribute-group/unit CRUD (add/remove/rename, the max-3-axes
 * cap) lives in UnitAttributeSetup now and is covered by its own spec.
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

  it('generateVariants() creates the cartesian product across two attribute axes', () => {
    const component = createComponent();
    component.attributeGroups.set([
      { name: 'Kích cỡ', values: ['S', 'M'] },
      { name: 'Màu sắc', values: ['Đen', 'Trắng'] },
    ]);

    component.generateVariants();

    expect(component.variantModeEnabled()).toBe(true);
    expect(component.variantRows()).toHaveLength(4);
    const keys = component.variantRows().map((r) => `${r.attributeValues['Kích cỡ']}|${r.attributeValues['Màu sắc']}`);
    expect(keys.sort()).toEqual(['M|Đen', 'M|Trắng', 'S|Đen', 'S|Trắng'].sort());
  });

  it('generateVariants() works with a single axis (e.g. a non-fashion "Hương vị" attribute)', () => {
    const component = createComponent();
    component.attributeGroups.set([{ name: 'Hương vị', values: ['Dâu', 'Vani'] }]);

    component.generateVariants();

    expect(component.variantRows()).toHaveLength(2);
    expect(component.variantRows().map((r) => r.attributeValues['Hương vị']).sort()).toEqual(['Dâu', 'Vani']);
  });

  it('generateVariants() re-run preserves manually-edited rows instead of resetting them', () => {
    const component = createComponent();
    component.attributeGroups.set([
      { name: 'Kích cỡ', values: ['S', 'M'] },
      { name: 'Màu sắc', values: ['Đen'] },
    ]);
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
    component.attributeGroups.set([
      { name: 'Kích cỡ', values: ['S'] },
      { name: 'Màu sắc', values: ['Đen'] },
    ]);
    component.generateVariants();
    component.updateVariantRow(0, { price: 500_000 });

    component.attributeGroups.update((groups) => groups.map((g, i) => (i === 1 ? { ...g, values: ['Đen', 'Trắng'] } : g)));
    component.generateVariants();

    expect(component.variantRows()).toHaveLength(2);
    expect(component.variantRows().find((r) => r.attributeValues['Màu sắc'] === 'Đen')?.price).toBe(500_000);
    expect(component.variantRows().find((r) => r.attributeValues['Màu sắc'] === 'Trắng')).toBeTruthy();
  });

  it('removeVariantRow() drops just that row', () => {
    const component = createComponent();
    component.attributeGroups.set([{ name: 'Kích cỡ', values: ['S'] }, { name: 'Màu sắc', values: ['Đen', 'Trắng'] }]);
    component.generateVariants();

    component.removeVariantRow(0);

    expect(component.variantRows()).toHaveLength(1);
  });

  it('cancelVariantMode() clears rows, units and turns variant mode off', () => {
    const component = createComponent();
    component.attributeGroups.set([{ name: 'Kích cỡ', values: ['S'] }, { name: 'Màu sắc', values: ['Đen'] }]);
    component.units.set([{ name: 'Hộp', conversionFactor: 1, price: 7000, sellDirectly: true }]);
    component.generateVariants();

    component.cancelVariantMode();

    expect(component.variantModeEnabled()).toBe(false);
    expect(component.variantRows()).toHaveLength(0);
    expect(component.units()).toHaveLength(0);
    expect(component.attributeGroups()).toEqual([{ name: '', values: [] }]);
  });

  it('entering variant mode disables the top-level sku/slug/price/stockQuantity controls', () => {
    const component = createComponent();
    component.attributeGroups.set([{ name: 'Kích cỡ', values: ['S'] }, { name: 'Màu sắc', values: ['Đen'] }]);

    component.generateVariants();
    TestBed.flushEffects();

    expect(component.form.controls.sku.disabled).toBe(true);
    expect(component.form.controls.slug.disabled).toBe(true);
    expect(component.form.controls.price.disabled).toBe(true);
    expect(component.form.controls.stockQuantity.disabled).toBe(true);
  });

  it('cancelling variant mode re-enables those controls', () => {
    const component = createComponent();
    component.attributeGroups.set([{ name: 'Kích cỡ', values: ['S'] }, { name: 'Màu sắc', values: ['Đen'] }]);
    component.generateVariants();
    TestBed.flushEffects();

    component.cancelVariantMode();
    TestBed.flushEffects();

    expect(component.form.controls.sku.disabled).toBe(false);
    expect(component.form.controls.slug.disabled).toBe(false);
    expect(component.form.controls.price.disabled).toBe(false);
    expect(component.form.controls.stockQuantity.disabled).toBe(false);
  });

  describe('selling units (KiotViet "Đơn vị tính") folded into the same variant pipeline', () => {
    it('generates one row per unit, seeded from that unit\'s own price and conversionFactor x costPrice', () => {
      const component = createComponent();
      component.form.controls.costPrice.setValue(5000);
      component.units.set([
        { name: 'Hộp', conversionFactor: 1, price: 7000, sellDirectly: true },
        { name: 'Lốc', conversionFactor: 4, price: 27000, sellDirectly: true },
      ]);

      component.generateVariants();

      expect(component.variantRows()).toHaveLength(2);
      const hop = component.variantRows().find((r) => r.attributeValues['Đơn vị tính'] === 'Hộp')!;
      const loc = component.variantRows().find((r) => r.attributeValues['Đơn vị tính'] === 'Lốc')!;
      expect(hop.price).toBe(7000);
      expect(hop.costPrice).toBe(5000);
      expect(loc.price).toBe(27000);
      expect(loc.costPrice).toBe(20000);
    });

    it('marks a row inactive when its unit is not "Bán trực tiếp"', () => {
      const component = createComponent();
      component.units.set([{ name: 'Thùng', conversionFactor: 48, price: 320000, sellDirectly: false }]);

      component.generateVariants();

      expect(component.variantRows()[0].active).toBe(false);
    });

    it('crosses units with a real attribute axis, units outermost in row order and last in column order', () => {
      const component = createComponent();
      component.attributeGroups.set([{ name: 'Hương vị', values: ['Dâu', 'Vani'] }]);
      component.units.set([
        { name: 'Hộp', conversionFactor: 1, price: 7000, sellDirectly: true },
        { name: 'Lốc', conversionFactor: 4, price: 27000, sellDirectly: true },
      ]);

      component.generateVariants();

      expect(component.variantColumnNames()).toEqual(['Hương vị', 'Đơn vị tính']);
      const order = component.variantRows().map((r) => `${r.attributeValues['Hương vị']}-${r.attributeValues['Đơn vị tính']}`);
      expect(order).toEqual(['Dâu-Hộp', 'Vani-Hộp', 'Dâu-Lốc', 'Vani-Lốc']);
    });
  });
});
