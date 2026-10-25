import { useState } from 'react';
import { useAnnouncedVersion } from '../api/newVersion';
import { usePollServerVersion } from '../api/queries/world';
import { Button } from '../components/Button';
import { IconButton } from '../components/IconButton';
import { useI18n } from '../i18n';
import styles from './NewVersionBanner.module.css';

/**
 * A strip at the top when a patch went live while this tab was open (1.6.3): the tab still runs the
 * old client, which fits the new server, so it offers the reload instead of forcing it (a new
 * 1.x.0 still reloads at once, see ServerVersionWatch). Closing the strip hides it until another
 * version comes.
 */
export function NewVersionBanner() {
  const { t } = useI18n();
  usePollServerVersion(); // a sleeping tab learns of the update when it wakes up
  const version = useAnnouncedVersion();
  const [dismissed, setDismissed] = useState<string | null>(null);
  if (version === null || version === dismissed) {
    return null;
  }
  return (
    <aside className={styles.banner} role="status" aria-label={t.newVersion.label}>
      <p className={styles.text}>{t.newVersion.text(version)}</p>
      {/* secondary: it stays usable on an ended world, where the game's solid buttons are off */}
      <Button type="button" size="small" variant="secondary" onClick={() => window.location.reload()}>
        {t.newVersion.reload}
      </Button>
      <IconButton icon="close" label={t.common.close} onClick={() => setDismissed(version)} />
    </aside>
  );
}
