import { describe, expect, it } from 'vitest';
import { brokerUrl, destinations } from './stomp';

describe('brokerUrl', () => {
  it('uses the page origin with ws or wss', () => {
    expect(brokerUrl({ protocol: 'http:', host: 'localhost:5173' })).toBe('ws://localhost:5173/ws');
    expect(brokerUrl({ protocol: 'https:', host: 'bannerfall.de' })).toBe('wss://bannerfall.de/ws');
  });
});

describe('destinations', () => {
  // the server drops any other SEND or SUBSCRIBE (WebSocketConfig.DestinationGuard)
  it('names the guild chat queues the server allows', () => {
    expect(destinations.guildChat).toBe('/user/queue/guild-chat');
    expect(destinations.guildChatSend).toBe('/app/guild-chat.send');
  });

  it("names the topic of the host's popups", () => {
    expect(destinations.broadcast).toBe('/topic/broadcast');
  });

  it('names the queue of the read markers', () => {
    expect(destinations.chatSeen).toBe('/user/queue/chat-seen');
  });
});
