/**
 * Remembers which table columns a shop chose to see. Per browser, not per
 * account: it is a viewing preference, not store data, so it never leaves the
 * machine that set it.
 *
 * Every access is guarded - private windows, cleared site data and browsers
 * set to block storage all make these throw or come back empty, and a column
 * preference is never worth a broken screen.
 */
export function loadColumnPrefs(storageKey: string, fallback: string[]): string[] {
  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) {
      return fallback;
    }
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed) || parsed.some((key) => typeof key !== 'string') || parsed.length === 0) {
      return fallback;
    }
    return parsed as string[];
  } catch {
    return fallback;
  }
}

export function saveColumnPrefs(storageKey: string, keys: string[]): void {
  try {
    localStorage.setItem(storageKey, JSON.stringify(keys));
  } catch {
    // A preference that cannot be stored is not worth reporting.
  }
}
