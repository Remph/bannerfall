import { useId, useState } from 'react';
import { NavLink } from 'react-router';
import { errorMessage } from '../api/errors';
import { useCurrentAccount, useLogout } from '../api/queries/auth';
import { Button } from '../components/Button';
import { ErrorMessage } from '../components/ErrorMessage';
import { Icon } from '../components/Icon';
import { iconStyles, setIconStyle, useIconStyle } from '../components/iconStyle';
import { SegmentedControl } from '../components/SegmentedControl';
import { ServerClock } from '../components/ServerClock';
import { Sheet } from '../components/Sheet';
import { VersionTag } from '../components/VersionTag';
import { useI18n } from '../i18n';
import { SOURCE_URL } from '../game/version';
import { languageNames, languages } from '../i18n/language';
import { playerPath } from '../pages/ranking/rankingPaths';
import {
  applyPalette,
  applyThemeMode,
  loadPalette,
  loadThemeMode,
  palettes,
  themeModes,
  type Palette,
  type ThemeMode,
} from '../theme/theme';
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
  /**
   * The "My profile" row (1.5.1) at the top of the desktop gear menu; on phones the More sheet has
   * it as a tile. Off in the pre-game, which has no profile pages.
   */
  profile?: boolean;
}

export function MenuSheet({ kind, villageId, onClose, onOpenChat, onOpenWorldInfo, profile = false }: MenuSheetProps) {
  const { t } = useI18n();
  return (
    <Sheet open={kind !== null} onClose={onClose} title={kind === 'more' ? t.nav.more : t.nav.settings}>
      {kind === 'more' && onOpenChat !== undefined && (
        <MoreLinks villageId={villageId} onNavigate={onClose} onOpenChat={onOpenChat} />
      )}
      <Settings onNavigate={onClose} onOpenWorldInfo={onOpenWorldInfo} profile={profile && kind === 'settings'} />
    </Sheet>
  );
}

interface SettingsProps {
  onNavigate: () => void;
  onOpenWorldInfo?: () => void;
  profile: boolean;
}

function Settings({ onNavigate, onOpenWorldInfo, profile }: SettingsProps) {
  const { t, language, setLanguage } = useI18n();
  const me = useCurrentAccount();
  const logout = useLogout();
  const [theme, setTheme] = useState<ThemeMode>(loadThemeMode);
  const [palette, setPalette] = useState<Palette>(loadPalette);
  const paletteLabel = useId();
  const iconStyle = useIconStyle();

  function changeTheme(mode: ThemeMode) {
    setTheme(mode);
    applyThemeMode(mode);
  }

  function changePalette(next: Palette) {
    setPalette(next);
    applyPalette(next);
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
      <div className={styles.setting}>
        <span className={styles.label} id={paletteLabel}>
          {t.settings.palette}
        </span>
        <PalettePicker labelId={paletteLabel} value={palette} onChange={changePalette} />
      </div>
      {/* medieval silhouettes or the classic line icons (1.6) */}
      <div className={styles.setting}>
        <span className={styles.label}>{t.settings.icons}</span>
        <SegmentedControl
          label={t.settings.icons}
          options={iconStyles.map((value) => ({ value, label: t.settings.iconStyles[value] }))}
          value={iconStyle}
          onChange={setIconStyle}
        />
      </div>
      <ul className={styles.rows}>
        {profile && (
          <li>
            <NavLink end className={styles.row} to={playerPath(me.playerId)} onClick={onNavigate}>
              <Icon name="profile" className={styles.rowIcon} />
              <span>{t.nav.profile}</span>
              <Icon name="chevronRight" className={styles.rowChevron} />
            </NavLink>
          </li>
        )}
        {onOpenWorldInfo !== undefined && (
          <li>
            <button type="button" className={styles.row} onClick={onOpenWorldInfo}>
              <Icon name="overview" className={styles.rowIcon} />
              <span>{t.settings.worldInfo}</span>
              <Icon name="chevronRight" className={styles.rowChevron} />
            </button>
          </li>
        )}
        <li>
          <NavLink end className={styles.row} to="/notifications" onClick={onNavigate}>
            <Icon name="bell" className={styles.rowIcon} />
            <span>{t.push.link}</span>
            <Icon name="chevronRight" className={styles.rowChevron} />
          </NavLink>
        </li>
        <li>
          <NavLink end className={styles.row} to="/password" onClick={onNavigate}>
            <Icon name="settings" className={styles.rowIcon} />
            <span>{t.settings.changePassword}</span>
            <Icon name="chevronRight" className={styles.rowChevron} />
          </NavLink>
        </li>
        <li>
          <NavLink end className={styles.row} to="/about" onClick={onNavigate}>
            <Icon name="info" className={styles.rowIcon} />
            <span>{t.about.link}</span>
            <Icon name="chevronRight" className={styles.rowChevron} />
          </NavLink>
        </li>
        <li>
          <NavLink end className={styles.row} to="/privacy" onClick={onNavigate}>
            <Icon name="lock" className={styles.rowIcon} />
            <span>{t.privacy.link}</span>
            <Icon name="chevronRight" className={styles.rowChevron} />
          </NavLink>
        </li>
        <li>
          {/* the public source code (1.6), in a new tab; on desktop also in the footer */}
          <a className={styles.row} href={SOURCE_URL} target="_blank" rel="noopener" title={t.settings.sourceTitle}>
            <Icon name="code" className={styles.rowIcon} />
            <span>{t.settings.source}</span>
            <Icon name="external" className={styles.rowChevron} />
          </a>
        </li>
        {me.role === 'ADMIN' && (
          <li>
            <NavLink end className={styles.row} to="/admin" onClick={onNavigate}>
              <Icon name="shield" className={styles.rowIcon} />
              <span>{t.admin.link}</span>
              <Icon name="chevronRight" className={styles.rowChevron} />
            </NavLink>
          </li>
        )}
      </ul>
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

interface PalettePickerProps {
  /** The id of the setting's label, which names the group. */
  labelId: string;
  value: Palette;
  onChange: (palette: Palette) => void;
}

/**
 * The colour themes (1.6) as small tiles, each a preview of that theme in the current light or dark
 * mode: its bar with the gold line and a main button on its paper (data-palette on the tile,
 * styles/palettes.css). Native radio buttons, so arrow keys move between them; the chosen theme's
 * name stands under the tiles, so nothing depends on hovering.
 */
function PalettePicker({ labelId, value, onChange }: PalettePickerProps) {
  const { t } = useI18n();
  return (
    <div className={styles.palettePicker}>
      <div className={styles.palettes} role="radiogroup" aria-labelledby={labelId}>
        {palettes.map((palette) => (
          <label key={palette} className={styles.palette}>
            <input
              type="radio"
              name={labelId}
              value={palette}
              checked={palette === value}
              onChange={() => onChange(palette)}
              aria-label={t.settings.palettes[palette]}
            />
            <span className={styles.paletteTile} data-palette={palette} aria-hidden="true">
              <span className={styles.paletteBar} />
              <span className={styles.paletteButton} />
            </span>
          </label>
        ))}
      </div>
      <span className={styles.paletteName}>{t.settings.palettes[value]}</span>
    </div>
  );
}
