import { useEffect } from 'react';
import { useI18n } from '../i18n';
import { refreshPush } from './pushClient';

/**
 * Once per start, account and language: a device that has notifications on signs up again, so the
 * server has it after a new password, a new world key or a language change (1.5).
 */
export function usePushRefresh(accountId: number): void {
  const { language } = useI18n();
  useEffect(() => {
    void refreshPush(language);
  }, [accountId, language]);
}
