import { Link } from 'react-router';
import { Card } from '../../components/Card';
import { Icon } from '../../components/Icon';
import { useI18n } from '../../i18n';
import styles from './AboutPage.module.css';

/** /about – what Bannerfall is, and the credits for fonts, graphics and software (05-frontend §3). */
export function AboutPage() {
  const { t } = useI18n();
  const about = t.about;
  return (
    <div className={styles.page}>
      <Link to="/" className={styles.back}>
        <Icon name="chevronLeft" /> {about.back}
      </Link>
      <h1>{about.title}</h1>
      <p className={styles.intro}>{about.intro}</p>
      <Card title={about.originalTitle}>
        <p className={styles.text}>{about.original}</p>
      </Card>
      <Card title={about.graphicsTitle}>
        <p className={styles.text}>{about.graphics}</p>
      </Card>
      <Card title={about.fontsTitle}>
        <p className={styles.text}>{about.fonts}</p>
      </Card>
      <Card title={about.softwareTitle}>
        <p className={styles.text}>{about.software}</p>
      </Card>
    </div>
  );
}
