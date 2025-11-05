/**
 * Color mapping utility for Vietnamese and English color names to hex codes
 * Used across the application for consistent color display
 */

export const COLOR_MAP: Record<string, string> = {
  // Vietnamese colors
  'Đen': '#000000',
  'Trắng': '#FFFFFF',
  'Xám': '#808080',
  'Xám Nhạt': '#D3D3D3',
  'Xám Đậm': '#4B5563',
  'Đỏ': '#EF4444',
  'Đỏ Đậm': '#DC2626',
  'Đỏ Đô': '#DC143C',
  'Cam': '#F97316',
  'Vàng': '#F59E0B',
  'Vàng Chanh': '#FFF44F',
  'Xanh Lá': '#10B981',
  'Xanh Lá Nhạt': '#90EE90',
  'Xanh Rêu': '#6B8E23',
  'Xanh Dương': '#3B82F6',
  'Xanh': '#3B82F6',
  'Xanh Navy': '#1E3A8A',
  'Xanh Nhạt': '#ADD8E6',
  'Xanh Ngọc': '#40E0D0',
  'Xanh Biển': '#1E90FF',
  'Xanh Cổ Vịt': '#008080',
  'Hồng': '#EC4899',
  'Hồng Đậm': '#FF1493',
  'Hồng Pastel': '#FFD1DC',
  'Tím': '#8B5CF6',
  'Tím Nhạt': '#E6E6FA',
  'Nâu': '#8B4513',
  'Be': '#F5F5DC',
  'Kem': '#FFFDD0',
  'Rêu': '#556B2F',
  'Bạc': '#C0C0C0',
  'Olive': '#808000',

  // English colors (fallback)
  'white': '#FFFFFF',
  'black': '#000000',
  'gray': '#808080',
  'grey': '#808080',
  'red': '#EF4444',
  'blue': '#3B82F6',
  'green': '#10B981',
  'yellow': '#F59E0B',
  'orange': '#F97316',
  'purple': '#8B5CF6',
  'pink': '#EC4899',
  'brown': '#8B4513',
  'beige': '#F5F5DC',
  'navy': '#1E3A8A',
  'teal': '#008080',
  'silver': '#C0C0C0',
};

/**
 * Get hex color code from color name
 * @param colorName - Vietnamese or English color name
 * @returns Hex color code (defaults to gray if not found)
 */
export function getColorHex(colorName: string): string {
  return COLOR_MAP[colorName] || COLOR_MAP[colorName.toLowerCase()] || '#D1D5DB';
}

/**
 * Check if a color is white or very light (needs darker border)
 * @param hexColor - Hex color code
 * @returns true if color is white or very light
 */
export function isLightColor(hexColor: string): boolean {
  const hex = hexColor.replace('#', '');
  const r = parseInt(hex.substr(0, 2), 16);
  const g = parseInt(hex.substr(2, 2), 16);
  const b = parseInt(hex.substr(4, 2), 16);

  // Calculate luminance
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;

  return luminance > 0.9; // Very light colors
}

/**
 * Check if a color is white
 * @param hexColor - Hex color code
 * @returns true if color is white
 */
export function isWhiteColor(hexColor: string): boolean {
  return hexColor.toUpperCase() === '#FFFFFF' || hexColor.toUpperCase() === '#FFF';
}
