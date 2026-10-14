import { useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../api/errors';
import { useCurrentAccount, useLogout } from '../api/queries/auth';
import { Button } from '../components/Button';
import { ErrorMessage } from '../components/ErrorMessage';
import { SegmentedControl } from '../components/SegmentedControl';
import { ServerClock } from '../components/ServerClock';
import { Sheet } from '../components/Sheet';
import { VersionTag } from '../components/VersionTag';
import { useI18n } from '../i18n';
import { languageNames, languages } from '../i18n/language';
import { applyThemeMode, loadThemeMode, themeModes, type ThemeMode } from '../theme/theme';
import styles from './MenuSheet.module.css';
import { MoreLinks } from './navigation';

/** 'more' = phone "More" tab (sections + settings), 'settings' = desktop gear (settings only). */
export type MenuKind = 'more' | 'settings';

interface MenuSheetProps {
  kind: MenuKind | null;
  villageId: number | undefined;
  onClose: () => void;
  /** Phone: the chat entry of "More" (desktop has its own chat panel). */
  onOpenChat?: () => void;
  /** The "World settings" entry (M14g); missing in the pre-game, where the page lists them. */
  onOpenWorldInfo?: () => void;
}

export function MenuSheet({ kind, villageId, onClose, onOpenChat, onOpenWorldInfo }: MenuSheetProps) {
  const { t } = useI18n();
  return (
    <Sheet open={kind !== null} onClose={onClose} title={kind === 'more' ? t.nav.more : t.nav.settings}>
      {kind === 'more' && onOpenChat !== undefined && (
        <MoreLinks villageId={villageId} onNavigate={onClose} onOpenChat={onOpenChat} />
      )}
      <Settings onNavigate={onClose} onOpenWorldInfo={onOpenWorldInfo} />
    </Sheet>
  );
}

function Settings({ onNavigate, onOpenWorldInfo }: { onNavigate: () => void; onOpenWorldInfo?: () => void }) {
  const { t, language, setLanguage } = useI18n();
  const me = useCurrentAccount();
  const logout = useLogout();
  const [theme, setTheme] = useState<ThemeMode>(loadThemeMode);

  function changeTheme(mode: ThemeMode) {
    setTheme(mode);
    applyThemeMode(mode);
  }

  return (
    <div className={styles.settings}>
      <div className={styles.setting}>
        <span className={styles.label}>{t.settings.language}</span>
        <SegmentedControl
          label={t.settings.language}
          options={languages.map((value) => ({ value, label: languageNames[value], lang: value }))}
          value={language}
          onChange={setLanguage}
        />
      </div>
      <div className={styles.setting}>
        <span className={styles.label}>{t.settings.theme}</span>
        <SegmentedControl
          label={t.settings.theme}
          options={themeModes.map((value) => ({ value, label: t.settings.themeModes[value] }))}
          value={theme}
          onChange={changeTheme}
        />
      </div>
      {onOpenWorldInfo !== undefined && (
        <Button type="button" variant="secondary" onClick={onOpenWorldInfo}>
          {t.settings.worldInfo}
        </Button>
      )}
      {me.role === 'ADMIN' && (
        <Link to="/admin" onClick={onNavigate}>
          {t.admin.link}
        </Link>
      )}
      <Link to="/password" onClick={onNavigate}>
        {t.settings.changePassword}
      </Link>
      <Link to="/about" onClick={onNavigate}>
        {t.about.link}
      </Link>
      <p className={styles.info}>
        {t.settings.serverTime}: <ServerClock /> <VersionTag />
      </p>
      <div className={styles.account}>
        <span className={styles.info}>{t.settings.loggedInAs(me.username)}</span>
        <Button type="button" variant="secondary" disabled={logout.isPending} onClick={() => logout.mutate()}>
          {t.auth.logout}
        </Button>
      </div>
      {/* e.g. offline: the player is still logged in and should know why nothing happened */}
      {logout.isError && <ErrorMessage>{errorMessage(t, logout.error)}</ErrorMessage>}
    </div>
  );
}
