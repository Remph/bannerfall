import { BackLink } from '../../components/BackLink';
import { Card } from '../../components/Card';
import { useI18n } from '../../i18n';
import styles from './AboutPage.module.css';

/** Where the medieval icons (1.6) come from, and their licence. */
const GAME_ICONS_URL = 'https://game-icons.net';
const CC_BY_3_URL = 'https://creativecommons.org/licenses/by/3.0/';

/**
 * /about – what Bannerfall is, the thanks to the test players (1.6) and the credits for fonts,
 * graphics and software (05-frontend §3).
 */
export function AboutPage() {
  const { t } = useI18n();
  const about = t.about;
  return (
    <div className={styles.page}>
      <BackLink to="/">{about.back}</BackLink>
      <h1>{about.title}</h1>
      <p className={styles.intro}>{about.intro}</p>
      <Card title={about.testersTitle}>
        <p className={styles.text}>{about.testers}</p>
        <p className={`${styles.text} ${styles.signature}`}>{about.testersSignature}</p>
      </Card>
      <Card title={about.originalTitle}>
        <p className={styles.text}>{about.original}</p>
      </Card>
      <Card title={about.graphicsTitle}>
        <p className={styles.text}>{about.graphics}</p>
        <p className={`${styles.text} ${styles.links}`}>
          <a href={GAME_ICONS_URL} target="_blank" rel="noopener">
            {about.graphicsSource}
          </a>
          <a href={CC_BY_3_URL} target="_blank" rel="noopener">
            {about.graphicsLicence}
          </a>
        </p>
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
