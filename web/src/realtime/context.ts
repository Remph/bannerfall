import { createContext, useContext } from 'react';
import type { ChatError } from '../api/queries/chat';

export interface RealtimeValue {
  /** The STOMP connection is up (the chat can send). */
  connected: boolean;
  /** Sends a chat message; false when not connected. A refusal comes back to the error listeners. */
  sendChat: (text: string) => boolean;
  /** Sends a message to the player's guild (M15b); false when not connected. */
  sendGuildChat: (text: string) => boolean;
  /** Listens for refused chat messages of this tab; returns the function that stops listening. */
  onChatError: (listener: (error: ChatError) => void) => () => void;
}

/** Filled by <RealtimeProvider> (inside the game screens, so only while logged in). */
export const RealtimeContext = createContext<RealtimeValue | null>(null);

export function useRealtime(): RealtimeValue {
  const value = useContext(RealtimeContext);
  if (value === null) {
    throw new Error('useRealtime() must be used inside <RealtimeProvider>');
  }
  return value;
}
