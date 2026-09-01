const PENDING_ORDER_KEY = 'storefront:pendingOrder';

interface PendingOrder {
  orderId: number;
  storeSlug: string;
}

/**
 * Carries orderId + storeSlug across the PayPal/MoMo external redirect
 * round-trip. The backend builds successUrl/cancelUrl itself
 * (frontendUrl + "/payment/success", no query string), so there is no other
 * channel to tell /payment/success which order it's looking at.
 */
export function savePendingOrder(order: PendingOrder): void {
  sessionStorage.setItem(PENDING_ORDER_KEY, JSON.stringify(order));
}

export function readPendingOrder(): PendingOrder | null {
  const raw = sessionStorage.getItem(PENDING_ORDER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as PendingOrder;
  } catch {
    return null;
  }
}

export function clearPendingOrder(): void {
  sessionStorage.removeItem(PENDING_ORDER_KEY);
}
