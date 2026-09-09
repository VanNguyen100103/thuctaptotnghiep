import { exportRowsToCsv } from './csv-export.util';
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

/** "Xuất file" on the product list - serializes whatever product list was already fetched. */
export function exportProductsToCsv(products: ProductDTO[], filename = 'hang-hoa.csv'): void {
  exportRowsToCsv(HEADERS, products.map(toRow), filename);
}
