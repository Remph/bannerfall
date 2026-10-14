import { describe, expect, it } from 'vitest';
import {
  isConversationPath,
  mergeMessages,
  newestIncomingId,
  olderMessagesBefore,
  prependOlderMessages,
} from './messages';

const line = (id: number, mine = false) => ({ id, mine, text: `m${id}` });

describe('mergeMessages', () => {
  it('keeps what was loaded and adds the new page, oldest first', () => {
    const merged = mergeMessages([line(1), line(2)], [line(2), line(3)]);
    expect(merged.map((message) => message.id)).toEqual([1, 2, 3]);
  });

  it('takes the fresh version of a message that is in both', () => {
    const merged = mergeMessages([{ id: 1, mine: false, text: 'old' }], [{ id: 1, mine: false, text: 'new' }]);
    expect(merged).toEqual([{ id: 1, mine: false, text: 'new' }]);
  });

  it('works without an earlier page', () => {
    expect(mergeMessages(undefined, [line(5), line(4)]).map((message) => message.id)).toEqual([4, 5]);
  });
});

describe('prependOlderMessages', () => {
  it('puts older messages in front', () => {
    expect(prependOlderMessages([line(3), line(4)], [line(1), line(2)]).map((message) => message.id)).toEqual([
      1, 2, 3, 4,
    ]);
  });
});

describe('olderMessagesBefore', () => {
  it('asks for the page before the first message once a full page is loaded', () => {
    const full = Array.from({ length: 100 }, (_, index) => line(index + 10));
    expect(olderMessagesBefore(full)).toBe(10);
  });

  it('has nothing to ask for when the page is short or empty', () => {
    expect(olderMessagesBefore([line(1), line(2)])).toBeUndefined();
    expect(olderMessagesBefore([])).toBeUndefined();
  });
});

describe('newestIncomingId', () => {
  it('is the newest message of the other player', () => {
    expect(newestIncomingId([line(1), line(2), line(3, true)])).toBe(2);
  });

  it('is 0 when the other player wrote nothing', () => {
    expect(newestIncomingId([line(1, true)])).toBe(0);
    expect(newestIncomingId([])).toBe(0);
  });
});

describe('isConversationPath', () => {
  it('matches only that conversation', () => {
    expect(isConversationPath('/messages/7', 7)).toBe(true);
    expect(isConversationPath('/messages/7', 8)).toBe(false);
    expect(isConversationPath('/messages', 7)).toBe(false);
  });
});
