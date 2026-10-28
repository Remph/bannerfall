import { describe, expect, it } from 'vitest';
import { BROADCASTS_KEPT, newestBroadcastId, unseenBroadcasts, withBroadcast } from './broadcast';

const b = (id: number) => ({ id, text: `message ${id}` });

describe('withBroadcast', () => {
  it('adds a broadcast in order of its id', () => {
    expect(withBroadcast([b(1), b(3)], b(2)).map((x) => x.id)).toEqual([1, 2, 3]);
  });

  it('ignores one that is there already (REST and STOMP both bring it)', () => {
    expect(withBroadcast([b(1), b(2)], b(2))).toEqual([b(1), b(2)]);
  });

  it('keeps the newest ones', () => {
    const many = Array.from({ length: BROADCASTS_KEPT }, (_, index) => b(index + 1));
    const next = withBroadcast(many, b(BROADCASTS_KEPT + 1));
    expect(next).toHaveLength(BROADCASTS_KEPT);
    expect(next[0]?.id).toBe(2);
  });
});

describe('unseenBroadcasts', () => {
  it('shows everything on a device that dismissed nothing yet', () => {
    expect(unseenBroadcasts([b(1), b(2)], null)).toEqual([b(1), b(2)]);
  });

  it('shows only what is newer than the last dismissed', () => {
    expect(unseenBroadcasts([b(1), b(2), b(3)], 2)).toEqual([b(3)]);
    expect(unseenBroadcasts([b(1), b(2)], 2)).toEqual([]);
  });
});

describe('newestBroadcastId', () => {
  it('is the highest id, null for an empty list', () => {
    expect(newestBroadcastId([b(5), b(9), b(7)])).toBe(9);
    expect(newestBroadcastId([])).toBeNull();
  });
});
