import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs';

/**
 * The STOMP connection to the server (05-frontend §7, 04-api *WebSocket*): one per logged-in tab.
 * `/ws` is a plain WebSocket on the page's own origin, authenticated by the session cookie (in
 * development through the Vite proxy). Only these destinations are allowed; the server drops any
 * other SUBSCRIBE or SEND without an answer.
 */
export const destinations = {
  events: '/user/queue/events',
  chat: '/topic/chat',
  errors: '/user/queue/errors',
  chatSend: '/app/chat.send',
  guildChat: '/user/queue/guild-chat',
  guildChatSend: '/app/guild-chat.send',
} as const;

/** `ws://host/ws` on http, `wss://host/ws` on https: the same origin as the page. */
export function brokerUrl(location: { protocol: string; host: string }): string {
  return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;
}

export interface RealtimeHandlers {
  /** A frame of `/user/queue/events`: a `GameNotification` (parsed JSON, not checked yet). */
  onEvent: (body: unknown) => void;
  /** A frame of `/topic/chat`: a `ChatMessageDto`, or `{hiddenId}` when the admin hid a message (M14a). */
  onChat: (body: unknown) => void;
  /** A frame of `/user/queue/guild-chat` (M15b): a `ChatMessageDto` of the guild, or `{hiddenId}`. */
  onGuildChat: (body: unknown) => void;
  /** A frame of `/user/queue/errors`: a `ChatErrorDto` for a refused chat message of this tab. */
  onChatError: (body: unknown) => void;
  /**
   * Connected and subscribed. `again` = not the first time: whatever the server pushed while the
   * connection was down is lost, so everything should be fetched again.
   */
  onConnected: (again: boolean) => void;
  /** The connection dropped (it tries again by itself). */
  onDisconnected: () => void;
  /**
   * An attempt to connect failed (it tries again by itself). The handshake needs a valid session:
   * after a server restart (sessions are in memory) every attempt fails until the player logs in
   * again.
   */
  onConnectFailed: () => void;
}

export interface RealtimeConnection {
  /** Sends a chat message; false when not connected (nothing was sent). */
  sendChat: (text: string) => boolean;
  /** Sends a message to the player's guild (M15b); false when not connected. */
  sendGuildChat: (text: string) => boolean;
  /** Closes the connection for good (logout, leaving the game screens). */
  close: () => void;
}

function parse(message: IMessage): unknown {
  try {
    return JSON.parse(message.body);
  } catch {
    return undefined; // not JSON: nothing the handlers could use
  }
}

/**
 * Opens the connection and keeps it open: after a drop it reconnects with a growing pause (1 s,
 * 2 s, 4 s … up to 30 s) and subscribes again. Server and client send heartbeats every 10 s (M14f;
 * stompjs' default), so a connection that died silently is noticed within about 20 s.
 */
export function connectRealtime(url: string, handlers: RealtimeHandlers): RealtimeConnection {
  let connectedBefore = false;
  let connected = false;
  const client = new Client({
    brokerURL: url,
    reconnectDelay: 1_000,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    maxReconnectDelay: 30_000,
    // without it a missed heartbeat only logs a warning and the dead socket stays until the browser
    // gives up on it (minutes): close it at the heartbeat timeout, so onWebSocketClose and the
    // reconnect follow within the ~20 s promised above
    discardWebsocketOnCommFailure: true,
    onConnect: () => {
      // subscriptions don't survive a reconnect: made anew every time
      client.subscribe(destinations.events, (message) => handlers.onEvent(parse(message)));
      client.subscribe(destinations.chat, (message) => handlers.onChat(parse(message)));
      client.subscribe(destinations.guildChat, (message) => handlers.onGuildChat(parse(message)));
      client.subscribe(destinations.errors, (message) => handlers.onChatError(parse(message)));
      connected = true;
      handlers.onConnected(connectedBefore);
      connectedBefore = true;
    },
    onWebSocketClose: () => {
      if (connected) {
        connected = false;
        handlers.onDisconnected();
      } else {
        handlers.onConnectFailed();
      }
    },
  });
  client.activate();
  const send = (destination: string, text: string): boolean => {
    if (!client.connected) {
      return false;
    }
    client.publish({
      destination,
      body: JSON.stringify({ text }),
      headers: { 'content-type': 'application/json' },
    });
    return true;
  };
  return {
    sendChat: (text) => send(destinations.chatSend, text),
    sendGuildChat: (text) => send(destinations.guildChatSend, text),
    close: () => {
      void client.deactivate();
    },
  };
}
