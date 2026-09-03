import { TestBed } from '@angular/core/testing';

import { UnitAttributeSetup } from './unit-attribute-setup';

/**
 * Covers what moved out of ProductForm's own spec when the units+attributes
 * builder became its own popup: the max-3-total-axes cap (units count as one
 * axis), unit add/remove, and the "+ Tạo thuộc tính mới" custom-name switch.
 * generateVariants() itself (the merge-by-key cartesian product) stays
 * covered in product-form.spec.ts, since that logic still lives on
 * ProductForm - this component only edits the shared units/attributeGroups
 * signals and asks the parent to regenerate via the `generate` output.
 */
describe('UnitAttributeSetup', () => {
  function createComponent() {
    const fixture = TestBed.createComponent(UnitAttributeSetup);
    fixture.componentRef.setInput('units', []);
    fixture.componentRef.setInput('attributeGroups', [{ name: '', values: [] }]);
    fixture.componentRef.setInput('variantRows', []);
    fixture.componentRef.setInput('variantColumnNames', []);
    fixture.componentRef.setInput('basePrice', 0);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('caps total axes (attribute groups + a unit axis, if any) at 3', () => {
    const component = createComponent();
    component.attributeGroups.set([
      { name: 'Kích cỡ', values: ['S'] },
      { name: 'Màu sắc', values: ['Đen'] },
    ]);
    expect(component.canAddAttributeGroup()).toBe(true);

    component.units.set([{ name: 'Hộp', conversionFactor: 1, price: 7000, sellDirectly: true }]);
    expect(component.totalAxisCount()).toBe(3);
    expect(component.canAddAttributeGroup()).toBe(false);

    component.addAttributeGroup(); // no-op past the cap
    expect(component.attributeGroups()).toHaveLength(2);
  });

  it('adding a unit appends to units() and asks the parent to regenerate', () => {
    const component = createComponent();
    const generated = vi.fn();
    component.generate.subscribe(generated);

    component.onUnitSaved({ unit: { name: 'Hộp', conversionFactor: 1, price: 7000, sellDirectly: true }, addAnother: false });

    expect(component.units()).toEqual([{ name: 'Hộp', conversionFactor: 1, price: 7000, sellDirectly: true }]);
    expect(component.unitFormOpen()).toBe(false);
    expect(generated).toHaveBeenCalled();
  });

  it('"addAnother" keeps the unit-form popup open for rapid entry', () => {
    const component = createComponent();
    component.openAddUnit();

    component.onUnitSaved({ unit: { name: 'Hộp', conversionFactor: 1, price: 7000, sellDirectly: true }, addAnother: true });

    expect(component.unitFormOpen()).toBe(true);
    expect(component.unitFormIsBase()).toBe(false); // a base unit now exists
  });

  it('removeUnitAt() drops just that unit and regenerates', () => {
    const component = createComponent();
    component.units.set([
      { name: 'Hộp', conversionFactor: 1, price: 7000, sellDirectly: true },
      { name: 'Lốc', conversionFactor: 4, price: 27000, sellDirectly: true },
    ]);
    const generated = vi.fn();
    component.generate.subscribe(generated);

    component.removeUnitAt(0);

    expect(component.units()).toEqual([{ name: 'Lốc', conversionFactor: 4, price: 27000, sellDirectly: true }]);
    expect(generated).toHaveBeenCalled();
  });

  it('picking "+ Tạo thuộc tính mới" switches that row to free-text name entry', () => {
    const component = createComponent();
    component.attributeGroups.set([{ name: '', values: [] }]);

    expect(component.isCustomNameEditing(0)).toBe(false);
    component.selectAttributeName(0, component.customNameOption);

    expect(component.isCustomNameEditing(0)).toBe(true);
    expect(component.attributeGroups()[0].name).toBe('');
  });

  it('attributeValueLabel() joins non-unit columns; unitValueOf()/conversionFactorOf() read the unit column', () => {
    const fixture = TestBed.createComponent(UnitAttributeSetup);
    fixture.componentRef.setInput('units', [{ name: 'Lốc', conversionFactor: 4, price: 27000, sellDirectly: true }]);
    fixture.componentRef.setInput('attributeGroups', [{ name: 'Hương vị', values: ['Dâu'] }]);
    fixture.componentRef.setInput('variantRows', []);
    fixture.componentRef.setInput('variantColumnNames', ['Hương vị', 'Đơn vị tính']);
    fixture.componentRef.setInput('basePrice', 0);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const row = {
      attributeValues: { 'Hương vị': 'Dâu', 'Đơn vị tính': 'Lốc' },
      sku: 'SKU1',
      barcode: '',
      price: 27000,
      costPrice: 20000,
      stockQuantity: 0,
      active: true,
    };

    expect(component.attributeValueLabel(row)).toBe('Dâu');
    expect(component.unitValueOf(row)).toBe('Lốc');
    expect(component.conversionFactorOf(row)).toBe(4);
  });
});
