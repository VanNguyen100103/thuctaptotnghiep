/** "Thời gian" filter: either one of the presets in the dropdown, or a hand-picked range. */
export type TimeMode = 'preset' | 'custom';

export type TimePreset = 'all' | 'today' | 'this-week' | 'this-month' | 'last-month';

export const TIME_PRESETS: { value: TimePreset; label: string }[] = [
  { value: 'all', label: 'Toàn thời gian' },
  { value: 'today', label: 'Hôm nay' },
  { value: 'this-week', label: 'Tuần này' },
  { value: 'this-month', label: 'Tháng này' },
  { value: 'last-month', label: 'Tháng trước' },
];

/**
 * The local calendar date as yyyy-MM-dd. Deliberately not toISOString(),
 * which prints UTC: a Vietnamese midnight is 17:00 the day before in UTC, so
 * "the 1st of this month" would go to the server as the last day of the
 * previous one.
 */
function isoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Both ends are inclusive; the backend widens `to` to the end of that day. */
export function presetRange(preset: TimePreset): { from: string | null; to: string | null } {
  const now = new Date();
  switch (preset) {
    case 'today':
      return { from: isoDate(now), to: isoDate(now) };
    case 'this-week': {
      // Monday-first, the way a Vietnamese week is counted (Date.getDay() puts Sunday at 0).
      const daysSinceMonday = (now.getDay() + 6) % 7;
      const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - daysSinceMonday);
      return { from: isoDate(monday), to: isoDate(now) };
    }
    case 'this-month':
      return { from: isoDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: isoDate(now) };
    case 'last-month': {
      const firstOfLast = new Date(now.getFullYear(), now.getMonth() - 1, 1);
      // Day 0 of this month is the last day of the previous one.
      const lastOfLast = new Date(now.getFullYear(), now.getMonth(), 0);
      return { from: isoDate(firstOfLast), to: isoDate(lastOfLast) };
    }
    case 'all':
    default:
      return { from: null, to: null };
  }
}

/** yyyy-MM-dd (what <input type="date"> holds) rendered the way a Vietnamese date is written. */
export function formatIsoDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return year && month && day ? `${day}/${month}/${year}` : iso;
}
