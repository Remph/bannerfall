import { describe, expect, it } from 'vitest';
import {
  appendLive,
  CHAT_MAX_LENGTH,
  CHAT_PAGE_SIZE,
  chatLength,
  chatLogFrom,
  clampChatText,
  isNearBottom,
  mergeLatest,
  newestId,
  olderPageBefore,
  prependOlder,
  serverSeenId,
  startsGroup,
  unreadCount,
  withoutMessage,
  withSeenMarker,
  type ChatLine,
} from './chat';

const ME = 1;
const OTHER = 2;

function line(id: number, playerId: number | null = OTHER): ChatLine {
  return { id, playerId };
}

/** Messages with the ids from..to (inclusive). */
function lines(from: number, to: number): ChatLine[] {
  return Array.from({ length: to - from + 1 }, (_, index) => line(from + index));
}

function ids(log: { messages: readonly ChatLine[] }): number[] {
  return log.messages.map((message) => message.id);
}

describe('chatLogFrom', () => {
  it('knows the start of the chat from a short page', () => {
    expect(chatLogFrom(lines(1, 3)).complete).toBe(true);
    expect(chatLogFrom(lines(1, CHAT_PAGE_SIZE)).complete).toBe(false);
  });

  it('sorts by id', () => {
    expect(ids(chatLogFrom([line(3), line(1), line(2)]))).toEqual([1, 2, 3]);
  });
});

describe('mergeLatest', () => {
  it('takes the page as it is when nothing is shown yet', () => {
    expect(ids(mergeLatest(undefined, lines(1, 3)))).toEqual([1, 2, 3]);
  });

  it('keeps older history and adds what was written while away', () => {
    const shown = prependOlder(chatLogFrom(lines(101, 200)), lines(1, 100));
    const merged = mergeLatest(shown, lines(151, 250));
    expect(ids(merged)).toEqual(lines(1, 250).map((message) => message.id));
    expect(merged.complete).toBe(false); // the page of 1–100 was full: maybe more before
  });

  it('drops what is shown when the fresh page starts after a gap', () => {
    const merged = mergeLatest(chatLogFrom(lines(1, 100)), lines(301, 400));
    expect(ids(merged)[0]).toBe(301);
    expect(merged.complete).toBe(false);
  });

  it('keeps live messages that came while the page was loading', () => {
    const shown = appendLive(chatLogFrom(lines(1, 100)), line(401));
    expect(ids(mergeLatest(shown, lines(301, 400)))).toEqual([...lines(301, 400).map((message) => message.id), 401]);
    expect(ids(mergeLatest(appendLive(chatLogFrom(lines(1, 3)), line(4)), lines(1, 3)))).toEqual([1, 2, 3, 4]);
  });

  it('takes a complete fresh page as the whole chat (messages may have been deleted)', () => {
    const merged = mergeLatest(chatLogFrom([line(1), line(2), line(3)]), [line(1), line(3)]);
    expect(ids(merged)).toEqual([1, 3]);
    expect(merged.complete).toBe(true);
  });

  it('drops a message hidden while away when the pages overlap', () => {
    const shown = prependOlder(chatLogFrom(lines(101, 200)), lines(1, 100));
    // 160 was hidden meanwhile: the fresh page of 100 messages skips it and reaches one further back
    const fresh = [...lines(150, 159), ...lines(161, 250)];
    const merged = mergeLatest(shown, fresh);
    expect(ids(merged)).not.toContain(160);
    expect(ids(merged)).toEqual([...lines(1, 159), ...lines(161, 250)].map((message) => message.id));
  });

  it('keeps shown messages outside the fresh page’s range', () => {
    const shown = appendLive(prependOlder(chatLogFrom(lines(101, 200)), lines(1, 100)), line(400));
    const merged = mergeLatest(shown, lines(151, 250));
    expect(ids(merged).slice(0, 3)).toEqual([1, 2, 3]);
    expect(ids(merged).at(-1)).toBe(400);
    expect(merged.messages).toHaveLength(251);
  });
});

describe('prependOlder', () => {
  it('puts the older page in front and knows when the start is reached', () => {
    const log = prependOlder(chatLogFrom(lines(101, 200)), lines(51, 100));
    expect(ids(log)[0]).toBe(51);
    expect(log.messages).toHaveLength(150);
    expect(log.complete).toBe(true);
  });
});

describe('appendLive', () => {
  it('adds a new message at the end, once', () => {
    const log = appendLive(chatLogFrom(lines(1, 2)), line(3));
    expect(ids(log)).toEqual([1, 2, 3]);
    expect(appendLive(log, line(3))).toBe(log);
  });
});

describe('olderPageBefore', () => {
  it('is the oldest id while older messages may exist', () => {
    expect(olderPageBefore(chatLogFrom(lines(5, 104)))).toBe(5);
    expect(olderPageBefore(chatLogFrom(lines(1, 3)))).toBeUndefined();
    expect(olderPageBefore({ messages: [], complete: false })).toBeUndefined();
  });
});

describe('unreadCount and newestId', () => {
  it('counts newer messages of other players only', () => {
    const messages = [line(1), line(2, ME), line(3), line(4)];
    expect(unreadCount(messages, 1, ME)).toBe(2);
    expect(unreadCount(messages, newestId(messages), ME)).toBe(0);
    expect(unreadCount([line(5, ME)], 0, ME)).toBe(0);
  });

  it('counts a broadcast by the host as unread too', () => {
    expect(unreadCount([line(1, null), line(2, ME)], 0, ME)).toBe(1);
  });

  it('newestId of an empty chat is 0', () => {
    expect(newestId([])).toBe(0);
  });
});

describe('isNearBottom', () => {
  it('allows a little slack', () => {
    expect(isNearBottom(500, 300, 800)).toBe(true);
    expect(isNearBottom(470, 300, 800)).toBe(true);
    expect(isNearBottom(300, 300, 800)).toBe(false);
  });
});

describe('startsGroup', () => {
  const at = (playerId: number | null, time: string) => ({ playerId, createdAt: `2026-10-06T${time}Z` });

  it('starts a block for the first message, another player or a pause over five minutes', () => {
    expect(startsGroup(undefined, at(ME, '20:00:00'))).toBe(true);
    expect(startsGroup(at(OTHER, '20:00:00'), at(ME, '20:00:10'))).toBe(true);
    expect(startsGroup(at(ME, '20:00:00'), at(ME, '20:05:01'))).toBe(true);
  });

  it('continues the block of the same player', () => {
    expect(startsGroup(at(ME, '20:00:00'), at(ME, '20:05:00'))).toBe(false);
  });

  it('keeps broadcasts of the host apart from the players', () => {
    expect(startsGroup(at(ME, '20:00:00'), at(null, '20:00:10'))).toBe(true);
    expect(startsGroup(at(null, '20:00:00'), at(ME, '20:00:10'))).toBe(true);
    expect(startsGroup(at(null, '20:00:00'), at(null, '20:00:10'))).toBe(false);
  });
});

describe('chatLength and clampChatText', () => {
  it('counts code points like the server: an emoji is one character', () => {
    expect(chatLength('Hallo')).toBe(5);
    expect(chatLength('⚔️🏰')).toBe(3); // crossed swords + variation selector, castle
    expect(chatLength('🏰'.repeat(CHAT_MAX_LENGTH))).toBe(CHAT_MAX_LENGTH);
  });

  it('cuts at the limit without splitting an emoji', () => {
    const full = '🏰'.repeat(CHAT_MAX_LENGTH);
    expect(clampChatText(full)).toBe(full);
    expect(clampChatText(`${full}🏰`)).toBe(full);
    expect(clampChatText(`${'a'.repeat(CHAT_MAX_LENGTH - 1)}🏰🏰`)).toBe(`${'a'.repeat(CHAT_MAX_LENGTH - 1)}🏰`);
  });
});

describe('withoutMessage', () => {
  it('cuts one message out and keeps the rest and the flag', () => {
    const log = { messages: lines(1, 3), complete: true };
    const cut = withoutMessage(log, 2);
    expect(ids(cut)).toEqual([1, 3]);
    expect(cut.complete).toBe(true);
    expect(ids(withoutMessage(log, 99))).toEqual([1, 2, 3]);
  });
});

describe('serverSeenId', () => {
  const markers = [
    { guildId: null, id: 40 },
    { guildId: 3, id: 25 },
  ];

  it('finds the marker of the world and of a guild', () => {
    expect(serverSeenId(markers)).toBe(40);
    expect(serverSeenId(markers, 3)).toBe(25);
  });

  it('is null for a channel nobody has read', () => {
    expect(serverSeenId(markers, 4)).toBeNull();
    expect(serverSeenId([])).toBeNull();
  });
});

describe('withSeenMarker', () => {
  it('adds a marker and moves one forward', () => {
    expect(withSeenMarker([], { guildId: null, id: 5 })).toEqual([{ guildId: null, id: 5 }]);
    expect(withSeenMarker([{ guildId: null, id: 5 }], { guildId: null, id: 9 })).toEqual([{ guildId: null, id: 9 }]);
  });

  it('never goes back to an older id and leaves the other channels alone', () => {
    const markers = [
      { guildId: null, id: 9 },
      { guildId: 3, id: 4 },
    ];
    const result = withSeenMarker(markers, { guildId: null, id: 5 });
    expect(serverSeenId(result)).toBe(9);
    expect(serverSeenId(result, 3)).toBe(4);
  });
});
