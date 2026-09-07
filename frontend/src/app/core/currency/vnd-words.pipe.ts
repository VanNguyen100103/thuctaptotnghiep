import { Pipe, PipeTransform } from '@angular/core';

const UNITS = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
/** Vietnamese groups digits in threes, then pairs "nghìn/triệu" against "tỷ" again past 10^12. */
const SCALES = ['', ' nghìn', ' triệu', ' tỷ', ' nghìn tỷ', ' triệu tỷ'];

/**
 * Reads one 3-digit group. `padded` is true for every group after the
 * leading one, where Vietnamese spells the zeros out ("một nghìn không trăm
 * linh năm") instead of dropping them.
 */
function readGroup(value: number, padded: boolean): string {
  const hundreds = Math.floor(value / 100);
  const tens = Math.floor((value % 100) / 10);
  const units = value % 10;
  const parts: string[] = [];

  if (hundreds > 0 || padded) {
    parts.push(`${UNITS[hundreds]} trăm`);
  }

  if (tens === 0) {
    if (units > 0 && (hundreds > 0 || padded)) {
      parts.push('linh');
    }
    if (units > 0) {
      parts.push(UNITS[units]);
    }
  } else if (tens === 1) {
    parts.push('mười');
    // "mười lăm", never "mười năm".
    if (units === 5) {
      parts.push('lăm');
    } else if (units > 0) {
      parts.push(UNITS[units]);
    }
  } else {
    parts.push(`${UNITS[tens]} mươi`);
    // The three digits that change form after a tens word: 21 "mốt",
    // 24 "tư", 25 "lăm" - the readings any Vietnamese invoice uses.
    if (units === 1) {
      parts.push('mốt');
    } else if (units === 4) {
      parts.push('tư');
    } else if (units === 5) {
      parts.push('lăm');
    } else if (units > 0) {
      parts.push(UNITS[units]);
    }
  }

  return parts.join(' ');
}

/** "650000" -> "Sáu trăm năm mươi nghìn đồng chẵn" - the amount-in-words line printed under a receipt's total. */
export function vndInWords(amount: number | null | undefined): string {
  const value = Math.round(Math.abs(amount ?? 0));
  if (value === 0) {
    return 'Không đồng';
  }

  const groups: number[] = [];
  for (let rest = value; rest > 0; rest = Math.floor(rest / 1000)) {
    groups.push(rest % 1000);
  }

  const words: string[] = [];
  for (let i = groups.length - 1; i >= 0; i--) {
    if (groups[i] === 0) {
      continue;
    }
    words.push(readGroup(groups[i], i < groups.length - 1) + (SCALES[i] ?? ''));
  }

  const text = words.join(' ').replace(/\s+/g, ' ').trim();
  return `${text.charAt(0).toUpperCase()}${text.slice(1)} đồng chẵn`;
}

@Pipe({ name: 'vndWords', standalone: true })
export class VndWordsPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return vndInWords(value);
  }
}
