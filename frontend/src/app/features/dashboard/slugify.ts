const COMBINING_DIACRITICS = /[̀-ͯ]/g;

/** "Áo Thun Basic" -> "ao-thun-basic". Diacritic-stripping via NFD normalization. */
export function slugify(name: string): string {
  return name
    .normalize('NFD')
    .replace(COMBINING_DIACRITICS, '')
    .replace(/đ/gi, 'd')
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/**
 * No backend SKU generation/uniqueness check exists - this is a best-effort
 * client-side suggestion (name-derived prefix + a short random suffix to
 * reduce collision odds), always editable before submit.
 */
export function suggestSku(name: string): string {
  const prefix =
    slugify(name)
      .split('-')
      .map((word) => word.slice(0, 3))
      .join('')
      .toUpperCase()
      .slice(0, 12) || 'SP';
  const suffix = Math.random().toString(36).slice(2, 6).toUpperCase();
  return `${prefix}-${suffix}`;
}
