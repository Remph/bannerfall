import { describe, expect, it } from 'vitest';
import { parseChatDockOpen } from './chatDockStorage';

describe('parseChatDockOpen', () => {
  it('is open only when stored so', () => {
    expect(parseChatDockOpen('open')).toBe(true);
    expect(parseChatDockOpen(null)).toBe(false);
    expect(parseChatDockOpen('minimized')).toBe(false);
  });
});
