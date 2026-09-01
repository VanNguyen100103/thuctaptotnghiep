import { Pipe, PipeTransform } from '@angular/core';

const formatter = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });

@Pipe({ name: 'vndCurrency', standalone: true })
export class VndCurrencyPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatter.format(value ?? 0);
  }
}
