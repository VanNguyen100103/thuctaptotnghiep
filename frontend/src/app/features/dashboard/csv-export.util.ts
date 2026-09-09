function escapeCsvField(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

/**
 * KiotViet's "Xuất file", done in the browser: there is no export endpoint on
 * the backend, so this serializes rows the screen already has.
 *
 * The UTF-8 BOM is what makes Excel read Vietnamese instead of mojibake - it
 * assumes the system codepage otherwise, and every "Hóa đơn" turns into
 * "HÃ³a Ä‘Æ¡n".
 */
export function exportRowsToCsv(headers: string[], rows: string[][], filename: string): void {
  const csv = [headers, ...rows].map((row) => row.map(escapeCsvField).join(',')).join('\r\n');
  const blob = new Blob([String.fromCharCode(0xfeff) + csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
