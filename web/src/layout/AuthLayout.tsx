import { Outlet } from 'react-router';
import { LanguageToggle } from '../components/LanguageToggle';
import { useT } from '../i18n';
import { PregameNotice } from '../pages/pregame/PregameNotice';
import styles from './AuthLayout.module.css';

/** Frame for the login and register pages. */
export function AuthLayout() {
  const t = useT();
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <LanguageToggle />
      </header>
      <main className={styles.main}>
        <p className={styles.brand}>{t.common.appName}</p>
        <div className={styles.card}>
          <Outlet />
        </div>
        <PregameNotice />
      </main>
    </div>
  );
}
