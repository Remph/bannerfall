import { describe, expect, it } from 'vitest';
import { conversationPath, MESSAGES_PATH, parseConversationPlayerId } from './messagePaths';

describe('message paths', () => {
  it('builds the inbox and conversation paths', () => {
    expect(MESSAGES_PATH).toBe('/messages');
    expect(conversationPath(12)).toBe('/messages/12');
  });

  it('reads a player id from the URL', () => {
    expect(parseConversationPlayerId('12')).toBe(12);
    expect(parseConversationPlayerId('0')).toBeUndefined();
    expect(parseConversationPlayerId('-3')).toBeUndefined();
    expect(parseConversationPlayerId('1x')).toBeUndefined();
    expect(parseConversationPlayerId(undefined)).toBeUndefined();
  });
});
