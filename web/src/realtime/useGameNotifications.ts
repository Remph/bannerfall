import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { useCurrentAccount } from '../api/queries/auth';
import { useShowToast } from '../components/toasts';
import { isConversationPath } from '../game/messages';
import { serverNow } from '../game/serverTime';
import { useI18n } from '../i18n';
import { notificationToast, parseNotification, staleQueries } from './notifications';

/**
 * What happens with a frame of `/user/queue/events` (05-frontend §7): the queries it makes outdated
 * are invalidated (those on screen load again right away), and some show a toast. Unknown types are
 * ignored.
 */
export function useGameNotifications(): (body: unknown) => void {
  const queryClient = useQueryClient();
  const showToast = useShowToast();
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  return useCallback(
    (body: unknown) => {
      const notification = parseNotification(body);
      if (notification === undefined) {
        return;
      }
      for (const queryKey of staleQueries(notification)) {
        void queryClient.invalidateQueries({ queryKey });
      }
      const toast = notificationToast(notification, t, me, serverNow(), language);
      // the conversation is open: the message is on screen already
      const onScreen =
        notification.type === 'MESSAGE_RECEIVED' &&
        isConversationPath(window.location.pathname, notification.fromPlayerId);
      if (toast !== undefined && !onScreen) {
        showToast(toast);
      }
    },
    [queryClient, showToast, t, language, me],
  );
}
