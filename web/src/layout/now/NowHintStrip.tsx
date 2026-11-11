import { useState } from 'react';
import { Icon } from '../../components/Icon';
import { IconButton } from '../../components/IconButton';
import { useT } from '../../i18n';
import styles from './NowHintStrip.module.css';
import { markNowHintSeen, nowHintSeen } from './nowHint';

/**
 * Phones (2.0): the one-time hint about the pull-down sheet, a strip in the page under the sky like the
 * changelog's (it used to float over the village's name, 2.0 UI review). It goes for good once the
 * player closes it or opens the sheet.
 */
export function NowHintStrip({ sheetOpen }: { sheetOpen: boolean }) {
  const t = useT();
  const [show, setShow] = useState(() => !nowHintSeen());
  // once the sheet has been open the hint has done its job (set while rendering; NowSheet stores it)
  if (sheetOpen && show) {
    setShow(false);
  }
  if (!show) {
    return null;
  }
  return (
    <aside className={styles.hint} aria-label={t.now.title}>
      <Icon name="chevronDown" />
      <p className={styles.text}>{t.now.hint}</p>
      <IconButton
        icon="close"
        label={t.now.hintOk}
        onClick={() => {
          setShow(false);
          markNowHintSeen();
        }}
      />
    </aside>
  );
}
