import { GOSHIP_STATUS_CODES, GOSHIP_STATUS_LABELS } from './shipment.models';

/**
 * This table is a hand-kept mirror of the backend GoshipShipmentStatus enum,
 * so the thing worth guarding is that it stays whole. A dropped code is not a
 * visible break: the filter simply never offers that status, and the first
 * draft of this list lost "Chuyển hoàn" and "Chậm lấy/giao" exactly that way.
 */
describe('Goship status table', () => {
  it('covers every documented code', () => {
    // 900-919 plus 1000, per doc.goship.io "Shipment status code".
    const documented = [...Array.from({ length: 20 }, (_, i) => 900 + i), 1000];
    expect([...GOSHIP_STATUS_CODES].sort((a, b) => a - b)).toEqual(documented);
  });

  it('gives every offered code a name', () => {
    for (const code of GOSHIP_STATUS_CODES) {
      expect(GOSHIP_STATUS_LABELS[code]).toBeTruthy();
    }
  });

  it('offers no code it cannot name', () => {
    expect(Object.keys(GOSHIP_STATUS_LABELS).length).toBe(GOSHIP_STATUS_CODES.length);
  });
});
