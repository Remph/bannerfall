import { Link, Outlet, useMatch } from 'react-router';
import { LanguageToggle } from '../components/LanguageToggle';
import { useT } from '../i18n';
import { PregameNotice } from '../pages/pregame/PregameNotice';
import styles from './AuthLayout.module.css';

/** Frame for the login and register pages, and for guests on the privacy page (wider, for reading). */
export function AuthLayout() {
  const t = useT();
  const onPrivacy = useMatch('/privacy') !== null;
  return (
    <div className={styles.page}>
      {/* 2.0: the same sky as in the game, higher, with the name of the game on it */}
      <header className={styles.sky}>
        <span className={styles.orb} aria-hidden="true" />
        <svg className={styles.hills} viewBox="0 0 400 30" preserveAspectRatio="none" aria-hidden="true">
          <path className={styles.hillFar} d="M0 30V15C60 3 110 2 170 12S300 25 400 7V30Z" />
          <path className={styles.hillNear} d="M0 30V23C80 11 150 25 230 19S340 9 400 21V30Z" />
        </svg>
        <div className={styles.tools}>
          <LanguageToggle />
        </div>
        <p className={styles.brand}>{t.common.appName}</p>
      </header>
      <main className={onPrivacy ? `${styles.main} ${styles.mainWide}` : styles.main}>
        <Outlet />
        <PregameNotice />
        {!onPrivacy && (
          <p className={styles.legal}>
            <Link to="/privacy">{t.privacy.link}</Link>
          </p>
        )}
      </main>
    </div>
  );
}
