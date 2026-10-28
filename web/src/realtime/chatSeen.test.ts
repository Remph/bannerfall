import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createSeenUploader, isChatSeenKey, parseSeenId, storageKey } from './chatSeen';

describe('parseSeenId', () => {
  it('reads a stored id', () => {
    expect(parseSeenId('42')).toBe(42);
    expect(parseSeenId('0')).toBe(0);
  });

  it('ignores nothing or garbage', () => {
    expect(parseSeenId(null)).toBeNull();
    expect(parseSeenId('')).toBeNull();
    expect(parseSeenId('-3')).toBeNull();
    expect(parseSeenId('abc')).toBeNull();
  });
});

describe('isChatSeenKey', () => {
  it('reacts to the seen ids of any player and to a cleared storage', () => {
    expect(isChatSeenKey('bannerfall.chatSeen.7')).toBe(true);
    expect(isChatSeenKey('bannerfall.guildChatSeen.7.3')).toBe(true);
    expect(isChatSeenKey(null)).toBe(true);
    expect(isChatSeenKey('bannerfall.chatDock')).toBe(false);
  });
});

describe('storageKey', () => {
  it('keeps one seen id per guild, so a new guild starts afresh', () => {
    expect(storageKey(7)).toBe('bannerfall.chatSeen.7');
    expect(storageKey(7, 3)).toBe('bannerfall.guildChatSeen.7.3');
    expect(storageKey(7, 3)).not.toBe(storageKey(7, 4));
    expect(isChatSeenKey(storageKey(7, 3))).toBe(true);
  });
});

describe('createSeenUploader', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  function setup() {
    const send = vi.fn<(playerId: number, guildId: number | undefined, id: number) => Promise<void>>(() =>
      Promise.resolve(),
    );
    return { send, uploader: createSeenUploader(send, 1000) };
  }

  it('sends only the newest id of a busy chat, once per delay', () => {
    const { send, uploader } = setup();
    uploader.queue(7, undefined, 10);
    uploader.queue(7, undefined, 12);
    uploader.queue(7, undefined, 11);
    expect(send).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1000);
    expect(send).toHaveBeenCalledExactlyOnceWith(7, undefined, 12);

    uploader.queue(7, undefined, 13);
    vi.advanceTimersByTime(1000);
    expect(send).toHaveBeenLastCalledWith(7, undefined, 13);
    expect(send).toHaveBeenCalledTimes(2);
  });

  it('keeps the channels and the players apart', () => {
    const { send, uploader } = setup();
    uploader.queue(7, undefined, 10);
    uploader.queue(7, 3, 20);
    uploader.queue(8, undefined, 30);

    vi.advanceTimersByTime(1000);
    expect(send.mock.calls).toEqual([
      [7, undefined, 10],
      [7, 3, 20],
      [8, undefined, 30],
    ]);
  });

  it('survives a failed request and sends the next one', async () => {
    const send = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue(undefined);
    const uploader = createSeenUploader(send, 1000);
    uploader.queue(7, undefined, 10);
    vi.advanceTimersByTime(1000);
    await Promise.resolve();

    uploader.queue(7, undefined, 11);
    vi.advanceTimersByTime(1000);
    expect(send).toHaveBeenLastCalledWith(7, undefined, 11);
  });
});
