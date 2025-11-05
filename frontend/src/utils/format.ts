/**
 * Utility functions for formatting values
 */

/**
 * Format price in Vietnamese currency format
 * @param price - Price in VND
 * @returns Formatted price string with currency symbol
 *
 * @example
 * formatPrice(199000) // "199.000₫"
 * formatPrice(1500000) // "1.500.000₫"
 */
export function formatPrice(price: number): string {
  if (price === null || price === undefined || isNaN(price)) {
    return '0₫';
  }

  // Format with thousands separator (dot) for Vietnamese format
  return price.toLocaleString('vi-VN') + '₫';
}

/**
 * Format price in Vietnamese currency format (alternative with currency code)
 * @param price - Price in VND
 * @returns Formatted price string with VND
 *
 * @example
 * formatPriceVND(199000) // "199.000 VND"
 */
export function formatPriceVND(price: number): string {
  if (price === null || price === undefined || isNaN(price)) {
    return '0 VND';
  }

  return price.toLocaleString('vi-VN') + ' VND';
}

/**
 * Format number with thousands separator
 * @param num - Number to format
 * @returns Formatted number string
 *
 * @example
 * formatNumber(1234567) // "1.234.567"
 */
export function formatNumber(num: number): string {
  if (num === null || num === undefined || isNaN(num)) {
    return '0';
  }

  return num.toLocaleString('vi-VN');
}

/**
 * Format percentage
 * @param value - Percentage value (0-100)
 * @param decimals - Number of decimal places (default: 0)
 * @returns Formatted percentage string
 *
 * @example
 * formatPercentage(33.333) // "33%"
 * formatPercentage(33.333, 2) // "33.33%"
 */
export function formatPercentage(value: number, decimals: number = 0): string {
  if (value === null || value === undefined || isNaN(value)) {
    return '0%';
  }

  return value.toFixed(decimals) + '%';
}

/**
 * Format date to Vietnamese locale
 * @param date - Date object or string
 * @returns Formatted date string
 *
 * @example
 * formatDate(new Date()) // "02/11/2025"
 */
export function formatDate(date: Date | string): string {
  if (!date) return '';

  const d = typeof date === 'string' ? new Date(date) : date;

  if (isNaN(d.getTime())) {
    return '';
  }

  return d.toLocaleDateString('vi-VN');
}

/**
 * Format datetime to Vietnamese locale
 * @param date - Date object or string
 * @returns Formatted datetime string
 *
 * @example
 * formatDateTime(new Date()) // "02/11/2025, 14:30:00"
 */
export function formatDateTime(date: Date | string): string {
  if (!date) return '';

  const d = typeof date === 'string' ? new Date(date) : date;

  if (isNaN(d.getTime())) {
    return '';
  }

  return d.toLocaleString('vi-VN');
}

/**
 * Format relative time (e.g., "2 hours ago")
 * @param date - Date object or string
 * @returns Relative time string
 *
 * @example
 * formatRelativeTime(new Date(Date.now() - 3600000)) // "1 giờ trước"
 */
export function formatRelativeTime(date: Date | string): string {
  if (!date) return '';

  const d = typeof date === 'string' ? new Date(date) : date;

  if (isNaN(d.getTime())) {
    return '';
  }

  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHour = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHour / 24);

  if (diffSec < 60) {
    return 'Vừa xong';
  } else if (diffMin < 60) {
    return `${diffMin} phút trước`;
  } else if (diffHour < 24) {
    return `${diffHour} giờ trước`;
  } else if (diffDay < 7) {
    return `${diffDay} ngày trước`;
  } else {
    return formatDate(d);
  }
}

/**
 * Truncate text with ellipsis
 * @param text - Text to truncate
 * @param maxLength - Maximum length
 * @returns Truncated text
 *
 * @example
 * truncateText("Hello World", 8) // "Hello..."
 */
export function truncateText(text: string, maxLength: number): string {
  if (!text) return '';
  if (text.length <= maxLength) return text;

  return text.substring(0, maxLength) + '...';
}
