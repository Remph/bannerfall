import type { Chronicle, ChronicleEntry, ChronicleMap, ChroniclePlayer } from '../api/queries/chronicle';
import type { Dictionary } from '../i18n/en';
import { bracketTag } from './guilds';
import { sideName } from './victory';

/*
 * The world chronicle (2.1 I1, 02-game-rules §32): pure helpers for the timeline and the replay map.
 * The server picks the turning points; these only read them.
 */

/** A village's owner in a replay state: a side's index in `map.sides`, or one of these. */
export const BARBARIAN = -1;
export const ABSENT = -2;

/**
 * Who owned which village at each frame: one array per frame, a value per village (the side's index,
 * {@link BARBARIAN} or {@link ABSENT} for a village that wasn't there yet or is gone). The server sends
 * each frame's changes only (side index + 1, 0 for barbarians, -1 gone).
 */
export function replayStates(map: ChronicleMap): Int32Array[] {
  const states: Int32Array[] = [];
  let state = new Int32Array(map.villages.length).fill(ABSENT);
  for (const frame of map.frames) {
    state = state.slice();
    for (let i = 0; i + 1 < frame.changes.length; i += 2) {
      const village = frame.changes[i] as number;
      const code = frame.changes[i + 1] as number;
      if (village >= 0 && village < state.length) {
        state[village] = code > 0 ? code - 1 : code === 0 ? BARBARIAN : ABSENT;
      }
    }
    states.push(state);
  }
  return states;
}

/** A side with its villages in one state. */
export interface SideCount {
  side: number;
  villages: number;
}

/** The sides with villages in a state, the most first (ties by index). */
export function sideCounts(state: Int32Array): SideCount[] {
  const counts = new Map<number, number>();
  for (const owner of state) {
    if (owner >= 0) {
      counts.set(owner, (counts.get(owner) ?? 0) + 1);
    }
  }
  return [...counts.entries()]
    .map(([side, villages]) => ({ side, villages }))
    .sort((a, b) => b.villages - a.villages || a.side - b.side);
}

/**
 * The legend under the replay: the biggest sides of the state, at most `max`, and the player's own
 * side even if it is smaller (it is the one he looks for).
 */
export function legend(state: Int32Array, mine: ReadonlySet<number>, max: number): SideCount[] {
  const all = sideCounts(state);
  const shown = all.slice(0, max);
  for (const entry of all.slice(max)) {
    if (mine.has(entry.side)) {
      shown.push(entry);
    }
  }
  return shown;
}

/** The frame to show for a timeline day: the last frame at or before it (the first one if none). */
export function frameForDay(map: ChronicleMap, day: number): number {
  let index = 0;
  map.frames.forEach((frame, i) => {
    if (frame.day <= day) {
      index = i;
    }
  });
  return index;
}

/** The timeline in days: the entries of each day together, the first day first. */
export function entriesByDay(entries: readonly ChronicleEntry[]): { day: number; entries: ChronicleEntry[] }[] {
  const days: { day: number; entries: ChronicleEntry[] }[] = [];
  for (const entry of entries) {
    const last = days[days.length - 1];
    if (last !== undefined && last.day === entry.day) {
      last.entries.push(entry);
    } else {
      days.push({ day: entry.day, entries: [entry] });
    }
  }
  return days;
}

/** The village of an entry on the replay map (its index in `map.villages`), if it has one there. */
export function entryVillageIndex(chronicle: Chronicle, entry: ChronicleEntry): number | null {
  const village = entry.facts.village;
  if (village == null) {
    return null;
  }
  const index = chronicle.map.villages.findIndex((dot) => dot.x === village.x && dot.y === village.y);
  return index < 0 ? null : index;
}

/** "Anna" or "[RED] Anna": a player with his guild's tag then. */
export function playerName(player: ChroniclePlayer | null | undefined, t: Dictionary): string {
  if (player == null) {
    return t.chronicle.someone;
  }
  return player.guildTag == null ? player.name : `${player.name} ${bracketTag(player.guildTag)}`;
}

/** What an entry says: one line, and short notes under it (the first conquest, a keep, a last village). */
export interface EntryText {
  line: string;
  notes: string[];
}

export function entryText(t: Dictionary, entry: ChronicleEntry): EntryText {
  const c = t.chronicle.entry;
  const f = entry.facts;
  const who = (player: ChroniclePlayer | null | undefined) => playerName(player, t);
  const side = (value: typeof f.side) => (value == null ? t.chronicle.someone : sideName(value, t));
  const village =
    f.village == null
      ? t.chronicle.aVillage
      : `${f.village.name ?? t.chronicle.barbarianVillage} (${f.village.x}|${f.village.y})`;
  switch (entry.kind) {
    case 'CONQUEST': {
      const notes: string[] = [];
      if (f.first === true) {
        notes.push(c.firstConquest);
      }
      if (f.keep === true) {
        notes.push(c.keep);
      }
      if (f.eliminated === true && f.from != null) {
        notes.push(c.lastVillage(who(f.from)));
      }
      return {
        line: f.from == null ? c.conquestBarbarian(who(f.to), village) : c.conquest(who(f.to), village, who(f.from)),
        notes,
      };
    }
    case 'CONQUESTS':
      return {
        line:
          f.to == null || (f.count ?? 0) <= 1
            ? c.conquests(f.count ?? 0)
            : c.conquestsBusiest(f.count ?? 0, who(f.to), f.topCount ?? 0),
        notes: [],
      };
    case 'GUILD_FOUNDED':
      // the founder by name alone: the guild's tag follows in the same line
      return { line: c.guildFounded(side(f.side), f.player?.name ?? t.chronicle.someone), notes: [] };
    case 'GUILD_DISBANDED':
      return { line: c.guildDisbanded(side(f.side)), notes: [] };
    case 'WAR':
      return { line: c.war(side(f.side), side(f.other)), notes: [] };
    case 'ALLY_JOINED':
      return { line: c.allyJoined(f.lord?.name ?? t.chronicle.someone, side(f.side)), notes: [] };
    case 'ALLY_LEFT':
      return {
        line:
          f.attacked === true
            ? c.allyLeftAttacked(f.lord?.name ?? t.chronicle.someone, side(f.side))
            : c.allyLeft(f.lord?.name ?? t.chronicle.someone, side(f.side)),
        notes: [],
      };
    case 'ORDER_ATTACK':
      return { line: c.orderAttack(f.lord?.name ?? t.chronicle.someone, who(f.player), village), notes: [] };
    case 'HELP_ANSWERED':
      return { line: c.helpAnswered(f.lord?.name ?? t.chronicle.someone, who(f.player), village), notes: [] };
    case 'HELP_IGNORED':
      return { line: c.helpIgnored(f.lord?.name ?? t.chronicle.someone, who(f.player), village), notes: [] };
    case 'COURT':
      return {
        line: f.other == null ? c.courtRose(side(f.side)) : c.courtPassed(side(f.side), side(f.other)),
        notes: [],
      };
    case 'LEAD':
      return { line: c.lead(side(f.side)), notes: [] };
    case 'VICTORY_CLOCK':
      return { line: c.victoryClock(side(f.side)), notes: [] };
    case 'WORLD_ENDED': {
      const reason = f.reason as keyof typeof t.victory.wonBy | null;
      return f.side == null
        ? { line: c.endedNobody, notes: [] }
        : {
            line: c.ended(side(f.side)),
            notes: reason != null && reason in t.victory.wonBy ? [t.victory.wonBy[reason]] : [],
          };
    }
  }
}

/** The sign of an entry in the timeline (an icon of the game's set). */
export function entryIcon(
  kind: ChronicleEntry['kind'],
): 'keep' | 'NOBLE' | 'guild' | 'shield' | 'mail' | 'ranking' | 'clock' | 'troops' {
  switch (kind) {
    case 'CONQUEST':
    case 'CONQUESTS':
      return 'NOBLE';
    case 'GUILD_FOUNDED':
    case 'GUILD_DISBANDED':
    case 'COURT':
      return 'guild';
    case 'WAR':
      return 'shield';
    case 'ALLY_JOINED':
    case 'ALLY_LEFT':
    case 'HELP_ANSWERED':
    case 'HELP_IGNORED':
      return 'mail';
    case 'ORDER_ATTACK':
      return 'troops';
    case 'LEAD':
      return 'ranking';
    case 'VICTORY_CLOCK':
      return 'clock';
    case 'WORLD_ENDED':
      return 'keep';
  }
}
