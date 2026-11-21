import { describe, expect, it } from 'vitest';
import { en } from '../i18n/en';
import { de } from '../i18n/de';
import { chronicleFixture } from '../testing/chronicleFixture';
import { ABSENT, BARBARIAN, entriesByDay, entryText, frameForDay, legend, replayStates, sideCounts } from './chronicle';

describe('the chronicle (2.1 I1)', () => {
  it('builds each day of the replay from the changes', () => {
    const states = replayStates(chronicleFixture.map).map((state) => [...state]);
    expect(states).toEqual([
      [0, 1, BARBARIAN],
      [0, 1, 0],
      [0, 0, 0],
      [0, 0, 0],
    ]);
  });

  it('marks a village that is not there yet or is gone', () => {
    const map = {
      ...chronicleFixture.map,
      frames: [
        { day: 0, at: '', changes: [0, 1] },
        { day: 1, at: '', changes: [1, 0, 0, -1] },
      ],
    };
    expect(replayStates(map).map((state) => [...state])).toEqual([
      [0, ABSENT, ABSENT],
      [ABSENT, BARBARIAN, ABSENT],
    ]);
  });

  it('counts the sides, the most first, and keeps the own side in the legend', () => {
    const state = Int32Array.from([1, 1, 1, 0, BARBARIAN, 2]);
    expect(sideCounts(state)).toEqual([
      { side: 1, villages: 3 },
      { side: 0, villages: 1 },
      { side: 2, villages: 1 },
    ]);
    expect(legend(state, new Set([2]), 1)).toEqual([
      { side: 1, villages: 3 },
      { side: 2, villages: 1 },
    ]);
  });

  it('shows the last frame at or before a day', () => {
    expect(frameForDay(chronicleFixture.map, 2)).toBe(2);
    expect(frameForDay(chronicleFixture.map, 9)).toBe(3);
    expect(frameForDay({ ...chronicleFixture.map, frames: chronicleFixture.map.frames.slice(2) }, 0)).toBe(0);
  });

  it('groups the timeline by day', () => {
    expect(entriesByDay(chronicleFixture.entries).map((day) => [day.day, day.entries.length])).toEqual([
      [1, 1],
      [2, 1],
      [3, 1],
    ]);
  });

  it('tells a conquest with its notes in both languages', () => {
    const [first, second] = chronicleFixture.entries;
    expect(entryText(en, first!)).toEqual({
      line: 'Anna took Barbarian village (8|8) from the barbarians.',
      notes: ['The first conquest of the world.', 'A banner keep changed hands.'],
    });
    expect(entryText(en, second!).line).toBe('Anna took Wulfsburg (5|5) from Boris.');
    expect(entryText(en, second!).notes).toEqual(['It was the last village of Boris.']);
    expect(entryText(de, second!).line).toBe('Anna nahm Boris Wulfsburg (5|5) ab.');
  });

  it('names a guild founder without repeating the tag of the guild just named', () => {
    const founded = {
      ...chronicleFixture.entries[0]!,
      kind: 'GUILD_FOUNDED' as const,
      facts: {
        ...chronicleFixture.entries[0]!.facts,
        side: { kind: 'GUILD' as const, id: 4, name: 'Iron Wolves', tag: 'IW', npc: false, court: null },
        player: { id: 1, name: 'Aldric', npc: false, guildId: 4, guildTag: 'IW' },
      },
    };
    expect(entryText(en, founded).line).toBe('Aldric founded [IW] Iron Wolves.');
    expect(entryText(de, founded).line).toBe('Aldric gründete [IW] Iron Wolves.');
  });

  it('tells the end with how it was won', () => {
    const end = chronicleFixture.entries[2]!;
    expect(entryText(en, end)).toEqual({
      line: 'The world ended. Anna won.',
      notes: ['Won with the most points at the end.'],
    });
  });
});
