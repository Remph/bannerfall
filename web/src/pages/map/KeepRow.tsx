import { Link } from 'react-router';
import type { MapVillage } from '../../api/queries/map';
import { useVictory } from '../../api/queries/world';
import { useI18n } from '../../i18n';
import { rankingPath } from '../ranking/rankingPaths';
import { stateText, useMySide } from '../ranking/victoryState';
import styles from './MapPage.module.css';

/**
 * A keep's row in the village panel (1.3): how the race stands for the side that holds it ("[IW] Iron
 * Wolves holds 3 of 5 keeps", "Your guild holds …"), with the way to the victory condition.
 */
export function KeepRow({ village }: { village: MapVillage }) {
  const { t, language } = useI18n();
  const victory = useVictory().data;
  const mine = useMySide();
  // the sides are guilds and lone players, or only players while guilds are off
  const holder = victory?.sides.find(
    (side) =>
      (side.kind === 'GUILD' && side.id === village.guildId) ||
      (side.kind === 'PLAYER' && side.id === village.playerId),
  );
  let text = t.map.keepValue;
  if (victory !== undefined && victory.mode === 'KEEPS') {
    text =
      holder === undefined || village.playerId === null
        ? t.victory.keepRowNone
        : stateText(t, victory, holder, mine(holder), language);
  }
  return (
    <div>
      <dt>{t.map.keep}</dt>
      <dd>
        {text}{' '}
        <Link to={rankingPath(0)} className={styles.keepLink}>
          {t.victory.toCondition} ›
        </Link>
      </dd>
    </div>
  );
}
