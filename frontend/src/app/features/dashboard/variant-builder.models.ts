/** Free-named attribute axis (e.g. "Kích cỡ": ["S","M","L"]) - see ProductForm#generateVariants. */
export interface AttributeGroup {
  name: string;
  values: string[];
}

/**
 * A KiotViet-style selling unit (Hộp/Lốc/Thùng). Not a distinct backend
 * concept - see UnitAttributeSetup: units are folded into the same
 * attribute-axis pipeline as AttributeGroup (an implicit "Đơn vị tính" axis),
 * so no new backend entity/migration is needed. The first unit added is
 * always the base unit (conversionFactor = 1).
 */
export interface UnitDef {
  name: string;
  /** How many base units make up one of this unit, e.g. Lốc = 4. Always 1 for the base unit. */
  conversionFactor: number;
  /** Giá bán entered for this unit - seeds the generated row(s)' price (still editable per-row after). Giá vốn is never entered here; it's always conversionFactor x base unit's giá vốn. */
  price: number;
  /** KiotViet's "Bán trực tiếp" - unchecked means this unit exists only as a conversion reference, not directly sellable (maps to the generated row's `active` flag). */
  sellDirectly: boolean;
}

export interface VariantRowDraft {
  /** Keys are the active attribute group names (plus "Đơn vị tính" when units are defined), e.g. {"Kích cỡ":"M","Màu sắc":"Đen"}. */
  attributeValues: Record<string, string>;
  sku: string;
  barcode: string;
  price: number;
  costPrice: number;
  stockQuantity: number;
  /** false when this row's unit value is marked "not sold directly" - see UnitDef#sellDirectly. */
  active: boolean;
}

export const MAX_ATTRIBUTE_GROUPS = 3;

/** Synthetic attribute-axis name units are folded in as - see ProductForm#generateVariants. */
export const UNIT_AXIS_NAME = 'Đơn vị tính';

/** Common Vietnamese attribute-name presets for the "Chọn thuộc tính" dropdown - no store-persisted vocabulary exists server-side, so this is a static, session-local list rather than KiotViet's real per-store history. */
export const ATTRIBUTE_NAME_PRESETS = ['Màu sắc', 'Kích cỡ', 'Hương vị', 'Loại', 'Chất liệu'];

/** "Chọn nhanh" quick-pick value presets for recognized attribute names. */
export const ATTRIBUTE_VALUE_PRESETS: Record<string, string[]> = {
  'Màu sắc': ['Đỏ', 'Xanh', 'Vàng', 'Đen', 'Trắng'],
  'Kích cỡ': ['S', 'M', 'L', 'XL', 'XXL'],
  'Hương vị': ['Dâu', 'Vani', 'Socola', 'Matcha'],
};
