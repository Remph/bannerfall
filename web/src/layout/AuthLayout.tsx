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
      <header className={styles.header}>
        <LanguageToggle />
      </header>
      <main className={onPrivacy ? `${styles.main} ${styles.mainWide}` : styles.main}>
        <p className={styles.brand}>{t.common.appName}</p>
        <div className={styles.card}>
          <Outlet />
        </div>
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
