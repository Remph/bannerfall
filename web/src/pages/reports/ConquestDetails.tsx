import { Link } from 'react-router';
import type { ConquestReport, ConqueredBanner } from '../../api/queries/reports';
import { Banner } from '../../components/Banner';
import { Card } from '../../components/Card';
import type { ReportKind } from '../../game/reports';
import { useT } from '../../i18n';
import { ReportVillage } from './BattleDetails';
import styles from './ReportsPage.module.css';

interface ConquestDetailsProps {
  kind: ReportKind;
  report: ConquestReport;
  /** The current village (map links keep it); undefined without a village. */
  villageId: number | undefined;
}

/**
 * A village changed owner (02-game-rules §10): for the conqueror (CONQUEST) with a link to his new
 * village, for the previous owner (VILLAGE_LOST) with what he lost. The battle itself is the
 * ATTACK / DEFENSE report of the same moment.
 */
export function ConquestDetails({ kind, report, villageId }: ConquestDetailsProps) {
  const t = useT();
  const won = kind === 'CONQUEST';
  return (
    <>
      <p className={[styles.verdict, styles.stamp, won ? styles.won : `${styles.lost} ${styles.shake}`].join(' ')}>
        {won ? t.reports.conquestWon : t.reports.conquestLost}
      </p>
      <div className={styles.columns}>
        <Card title={t.reports.conquestVillage}>
          <div className={styles.cardBody}>
            <ReportVillage village={report.village} villageId={villageId} />
            <dl className={styles.facts}>
              <div>
                <dt>{t.reports.conquestFrom}</dt>
                <dd>
                  <ReportVillage village={report.from} villageId={villageId} />
                </dd>
              </div>
              {report.village.name !== report.newName && (
                <div>
                  <dt>{t.reports.newName}</dt>
                  <dd>{report.newName}</dd>
                </div>
              )}
            </dl>
            {report.banner != null && <ConquestBanner banner={report.banner} won={won} />}
            {won && (
              <Link className={styles.action} to={`/v/${report.village.id}`}>
                {t.reports.toVillage}
              </Link>
            )}
          </div>
        </Card>
      </div>
    </>
  );
}

/**
 * The banner that flew in the village (M32b, 02-game-rules §21.6.1): into the conqueror's stash, or
 * left in the village (banners switched off, or an NPC lord took it).
 */
function ConquestBanner({ banner, won }: { banner: ConqueredBanner; won: boolean }) {
  const t = useT();
  const fate = won
    ? banner.toStash
      ? t.reports.conquestBannerToStash
      : t.reports.conquestBannerAsleep
    : banner.toStash
      ? t.reports.lostBannerTaken
      : t.reports.lostBannerStays;
  return (
    <div className={styles.group}>
      <h3 className={styles.groupTitle}>{t.reports.conquestBanner}</h3>
      <p className={styles.scoutedBanner}>
        <Banner type={banner.type} tier={banner.tier} size={20} decorative />
        {t.banners.label(t.banners.types[banner.type], banner.tier)}
      </p>
      <p className={won && banner.toStash ? styles.bannerWon : styles.muted}>{fate}</p>
    </div>
  );
}
