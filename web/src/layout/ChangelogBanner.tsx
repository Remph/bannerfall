import { useState } from 'react';
import { useWorld } from '../api/queries/world';
import { IconButton } from '../components/IconButton';
import { changelogUrl } from '../game/version';
import { useI18n } from '../i18n';
import styles from './ChangelogBanner.module.css';
import { loadChangelogSeen, saveChangelogSeen, versionToAnnounce } from './changelogSeen';

/**
 * A slim, dismissible strip under the top bar: "Version 1.1.0 is live – see what's new" with a
 * link to the changelog on bannerfall.de (M28). It shows once per version and browser: after the
 * host deploys a new build, until the player dismisses it or opens the changelog.
 */
export function ChangelogBanner() {
  const { t, language } = useI18n();
  const world = useWorld();
  const [seen, setSeen] = useState(loadChangelogSeen);
  const version = versionToAnnounce(world.data?.version, seen);
  if (version === null) {
    return null;
  }
  const markSeen = () => {
    saveChangelogSeen(version);
    setSeen(version);
  };
  return (
    <aside className={styles.banner} aria-label={t.changelogBanner.label}>
      <p className={styles.text}>
        {t.changelogBanner.text(version)}{' '}
        <a
          className={styles.link}
          href={changelogUrl(version, language)}
          target="_blank"
          rel="noopener"
          onClick={markSeen}
        >
          {t.changelogBanner.link}
        </a>
      </p>
      <IconButton icon="close" label={t.common.close} onClick={markSeen} />
    </aside>
  );
}
