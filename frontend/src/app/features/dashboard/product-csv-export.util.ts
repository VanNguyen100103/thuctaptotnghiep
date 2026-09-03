import { ProductDTO } from './product-admin.models';

const HEADERS = [
  'Mã hàng',
  'Mã vạch',
  'Tên hàng',
  'Danh mục',
  'Giá bán',
  'Giá vốn',
  'Tồn kho',
  'Khách đặt',
  'Trạng thái',
  'Thời gian tạo',
];

function escapeCsvField(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

function toRow(product: ProductDTO): string[] {
  return [
    product.sku,
    product.barcode ?? '',
    product.name,
    product.categories.map((c) => c.name).join(' / '),
    String(product.price),
    product.costPrice != null ? String(product.costPrice) : '',
    String(product.stockQuantity),
    String(product.pendingCustomerQuantity),
    product.active ? 'Đang bán' : 'Ngừng bán',
    new Date(product.createdAt).toLocaleString('vi-VN'),
  ];
}

/** Client-side CSV export (matches KiotViet's "Xuất file") - no export endpoint on the backend, so this just serializes whatever product list was already fetched. UTF-8 BOM so Excel opens Vietnamese text without mojibake. */
export function exportProductsToCsv(products: ProductDTO[], filename = 'hang-hoa.csv'): void {
  const csv = [HEADERS, ...products.map(toRow)].map((row) => row.map(escapeCsvField).join(',')).join('\r\n');
  const bom = String.fromCharCode(0xfeff);
  const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
