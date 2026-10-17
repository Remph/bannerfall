import { Link } from 'react-router';
import { Icon } from './Icon';
import styles from './BackLink.module.css';

/**
 * The one back link above a sub-page's title (1.3): a chevron and the name of the page it returns
 * to ("Village overview", "Rally Point"), the same on every page. The page cross-fades back.
 */
export function BackLink({ to, children }: { to: string; children: string }) {
  return (
    <Link viewTransition className={styles.back} to={to}>
      <Icon name="chevronLeft" /> {children}
    </Link>
  );
}
