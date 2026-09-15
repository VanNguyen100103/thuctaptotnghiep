import { TAX_STATUS_LABELS, declarationRoundLabel } from './tax.models';

/**
 * "Lần kê khai" reads off by one from what is stored: round 1 is the first
 * filing, round 2 is the FIRST amendment. Getting that wrong would print
 * "Bổ sung lần 2" on a shop's first correction and send it looking for an
 * amendment it never made.
 */
describe('declarationRoundLabel', () => {
  it('calls the first filing "Lần đầu"', () => {
    expect(declarationRoundLabel(1)).toBe('Lần đầu');
  });

  it('numbers amendments from one, not from the stored round', () => {
    expect(declarationRoundLabel(2)).toBe('Bổ sung lần 1');
    expect(declarationRoundLabel(4)).toBe('Bổ sung lần 3');
  });

  it('treats a missing or zero round as the first filing', () => {
    expect(declarationRoundLabel(0)).toBe('Lần đầu');
  });
});

describe('tax status labels', () => {
  it('names every status the list can render', () => {
    // A status with no label renders as blank in the Trạng thái column, which
    // reads as "nothing to do" on a return that is overdue.
    expect(TAX_STATUS_LABELS.DANG_CAP_NHAT).toBe('Đang cập nhật');
    expect(TAX_STATUS_LABELS.CHUA_NOP).toBe('Chưa nộp');
    expect(TAX_STATUS_LABELS.DA_NOP).toBe('Đã nộp');
    expect(Object.keys(TAX_STATUS_LABELS).length).toBe(3);
  });
});
