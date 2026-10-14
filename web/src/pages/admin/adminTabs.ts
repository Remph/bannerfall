/** The tabs of /admin (08-host-console §7), kept in the URL (`?tab=players`) so Back and reload keep them. */
export const adminTabs = ['status', 'players', 'world', 'events', 'lords', 'suspicious', 'cheats', 'audit'] as const;
export type AdminTab = (typeof adminTabs)[number];

/** The tabs to show: Cheats only while the server has them switched on. */
export function visibleTabs(cheatsEnabled: boolean): readonly AdminTab[] {
  return cheatsEnabled ? adminTabs : adminTabs.filter((tab) => tab !== 'cheats');
}

/** The tab of a `?tab=` value; Status for anything unknown or hidden. */
export function parseTab(value: string | null, cheatsEnabled: boolean): AdminTab {
  return visibleTabs(cheatsEnabled).find((tab) => tab === value) ?? 'status';
}

/** Uptime and lag in words: "42 s", "5 min", "3 h 5 min", "2 d 4 h" (like the CLI). */
export function shortDuration(seconds: number): string {
  if (seconds < 60) {
    return `${Math.max(0, Math.floor(seconds))} s`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 48) {
    return `${hours} h ${minutes % 60} min`;
  }
  return `${Math.floor(hours / 24)} d ${hours % 24} h`;
}
