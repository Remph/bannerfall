import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useEffectEvent, useMemo, useRef, useState, type ReactNode } from 'react';
import { meQueryKey } from '../api/queries/auth';
import {
  addChatMessage,
  chatQueryKey,
  myGuildIdIn,
  removeChatMessage,
  guildChatQueryKey,
  type ChatError,
  type ChatHidden,
  type ChatMessage,
} from '../api/queries/chat';
import { messagesQueryKey } from '../api/queries/messages';
import { reportsQueryKey } from '../api/queries/reports';
import { useShowToast } from '../components/toasts';
import { useT } from '../i18n';
import { RealtimeContext, type RealtimeValue } from './context';
import { brokerUrl, connectRealtime, type RealtimeConnection } from './stomp';
import { useGameNotifications } from './useGameNotifications';

function isChatMessage(body: unknown): body is ChatMessage {
  return typeof body === 'object' && body !== null && 'id' in body && 'text' in body && 'playerId' in body;
}

/** The admin hid a message (M14a): `{hiddenId}` on the same topic. */
function isChatHidden(body: unknown): body is ChatHidden {
  return typeof body === 'object' && body !== null && 'hiddenId' in body && typeof body.hiddenId === 'number';
}

function isChatError(body: unknown): body is ChatError {
  return typeof body === 'object' && body !== null && 'code' in body && typeof body.code === 'string';
}

/**
 * Keeps the STOMP connection of the game screens (05-frontend §7): opened when they mount (after
 * login), closed when they unmount (logout sends the player to /login). Notifications go to
 * useGameNotifications, chat messages into the `['chat']` query.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const handleNotification = useGameNotifications();
  const showToast = useShowToast();
  const t = useT();
  const [connected, setConnected] = useState(false);
  const connection = useRef<RealtimeConnection | null>(null);
  const chatErrorListeners = useRef(new Set<(error: ChatError) => void>());

  // Effect events: always see the latest props/state, without reconnecting when they change.
  const onEvent = useEffectEvent((body: unknown) => handleNotification(body));
  const onChat = useEffectEvent((body: unknown) => {
    if (isChatMessage(body)) {
      addChatMessage(queryClient, body);
      // a broadcast by the host (M14d), e.g. "restart in 5 minutes": also a toast, the chat may be closed
      if (body.playerId === null) {
        showToast({ text: t.chat.broadcastToast(body.text), tone: 'info' });
      }
    } else if (isChatHidden(body)) {
      removeChatMessage(queryClient, body.hiddenId);
    }
  });
  // the guild's own channel (M15b): filed under the guild the cache says the player is in
  const onGuildChat = useEffectEvent((body: unknown) => {
    const guildId = myGuildIdIn(queryClient);
    if (guildId === undefined) {
      return;
    }
    if (isChatMessage(body)) {
      addChatMessage(queryClient, body, guildChatQueryKey(guildId));
    } else if (isChatHidden(body)) {
      removeChatMessage(queryClient, body.hiddenId, guildChatQueryKey(guildId));
    }
  });
  const onConnected = useEffectEvent((again: boolean) => {
    setConnected(true);
    if (again) {
      // what the server pushed while the connection was down is lost: load everything again
      void queryClient.invalidateQueries();
    } else {
      // the first answers may have been sent before the subscriptions were made: what was pushed
      // in between is lost, so fetch the chat and the counters (red counter, unread reports) again
      for (const queryKey of [chatQueryKey, ['guildChat'], meQueryKey, reportsQueryKey, messagesQueryKey]) {
        void queryClient.invalidateQueries({ queryKey });
      }
    }
  });
  // the handshake needs a session: if it is gone (expired, or a server restart logged everyone
  // out), /api/me answers 401 and the player goes to /login. Asked on a drop and after every failed
  // attempt: right after a drop the server may still be down, and the attempts go on until it is back.
  const onDisconnected = useEffectEvent(() => {
    setConnected(false);
    void queryClient.invalidateQueries({ queryKey: meQueryKey });
  });
  const onConnectFailed = useEffectEvent(() => {
    void queryClient.invalidateQueries({ queryKey: meQueryKey });
  });

  useEffect(() => {
    const listeners = chatErrorListeners.current;
    const opened = connectRealtime(brokerUrl(window.location), {
      onEvent: (body) => onEvent(body),
      onChat: (body) => onChat(body),
      onGuildChat: (body) => onGuildChat(body),
      onChatError: (body) => {
        if (isChatError(body)) {
          listeners.forEach((listener) => listener(body));
        }
      },
      onConnected: (again) => onConnected(again),
      onDisconnected: () => onDisconnected(),
      onConnectFailed: () => onConnectFailed(),
    });
    connection.current = opened;
    return () => {
      opened.close();
      connection.current = null;
      setConnected(false);
    };
  }, []);

  const value = useMemo<RealtimeValue>(
    () => ({
      connected,
      sendChat: (text) => connection.current?.sendChat(text) ?? false,
      sendGuildChat: (text) => connection.current?.sendGuildChat(text) ?? false,
      onChatError: (listener) => {
        chatErrorListeners.current.add(listener);
        return () => chatErrorListeners.current.delete(listener);
      },
    }),
    [connected],
  );

  return <RealtimeContext value={value}>{children}</RealtimeContext>;
}
