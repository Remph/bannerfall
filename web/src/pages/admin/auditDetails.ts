/**
 * The details of an audit row in one line: `wood 1000 · clay 0`, a settings change as
 * `speed 1 → 2`. Values are whatever the server stored (no password is ever among them).
 */
export function auditDetails(details: Record<string, unknown>): string {
  return Object.entries(details)
    .map(([key, value]) => `${key} ${show(value)}`)
    .join(' · ');
}

function show(value: unknown): string {
  if (Array.isArray(value) && value.length === 2) {
    return `${show(value[0])} → ${show(value[1])}`;
  }
  if (value === null || value === undefined) {
    return '–';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value as string | number | boolean);
}
