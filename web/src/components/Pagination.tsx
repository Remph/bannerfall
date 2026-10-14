import { Link } from 'react-router';
import { Icon } from './Icon';
import styles from './Pagination.module.css';

interface PaginationProps {
  /** 0-based. */
  page: number;
  pages: number;
  /** The URL of a page (0-based); the page itself lives in the URL (`?page=2`). */
  pathFor: (page: number) => string;
  /** Names the navigation for screen readers. */
  label: string;
  previous: string;
  next: string;
  /** "Page 2 of 5" */
  info: string;
}

/** Previous / next with the page info between them (reports, ranking). */
export function Pagination({ page, pages, pathFor, label, previous, next, info }: PaginationProps) {
  return (
    <nav className={styles.pagination} aria-label={label}>
      {page > 0 ? (
        <Link className={styles.pageLink} to={pathFor(page - 1)}>
          <Icon name="chevronLeft" /> {previous}
        </Link>
      ) : (
        <span className={styles.pageLink} aria-disabled="true">
          <Icon name="chevronLeft" /> {previous}
        </span>
      )}
      <span className={styles.pageInfo}>{info}</span>
      {page < pages - 1 ? (
        <Link className={styles.pageLink} to={pathFor(page + 1)}>
          {next} <Icon name="chevronRight" />
        </Link>
      ) : (
        <span className={styles.pageLink} aria-disabled="true">
          {next} <Icon name="chevronRight" />
        </span>
      )}
    </nav>
  );
}
