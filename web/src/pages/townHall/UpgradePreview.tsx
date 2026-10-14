import type { BuildingInfo } from '../../api/queries/buildings';
import type { Village } from '../../api/queries/villages';
import { CostList } from '../../components/CostList';
import { Icon } from '../../components/Icon';
import type { ResourceAmounts } from '../../game/resources';
import { formatEffect, upgradeBlocker } from '../../game/upgradePreview';
import { useI18n } from '../../i18n';
import { blockerText } from './blockerText';
import styles from './UpgradePreview.module.css';

/** What the preview needs to know about the village to say whether an upgrade is possible. */
export interface PreviewContext {
  village: Village;
  stock: ResourceAmounts;
  freePop: number;
  queueFull: boolean;
  now: number;
  /** The world's setting: without banners no Banner Hall can be built (D11). */
  bannersEnabled: boolean;
}

interface BodyProps {
  info: BuildingInfo;
  context: PreviewContext;
}

/**
 * The upgrade preview (M24): the next level of one building – what it changes, what it unlocks, what
 * it costs – and, if it can't be queued right now, why. The same content in the hover popover of a
 * computer and in the sheet of a phone. Buildings that can't be upgraded yet show the same, with
 * what is missing instead of a green light.
 */
export function UpgradePreviewBody({ info, context }: BodyProps) {
  const { t, language } = useI18n();
  const next = info.next;
  if (next === null) {
    return <p className={styles.status}>{t.townHall.maxLevel}</p>;
  }
  const blocker = upgradeBlocker(
    info,
    context.village,
    context.freePop,
    context.queueFull,
    context.now,
    context.bannersEnabled,
  );
  const unlocks = [
    ...next.unlocksBuildings.map((building) => t.buildings[building]),
    ...next.unlocksUnits.map((unit) => t.units[unit]),
  ];
  return (
    <div className={styles.body}>
      <p className={styles.levels}>
        <Icon name={info.building} className={styles.levelIcon} />
        <span>{t.buildings[info.building]}</span>
        <span className={styles.levelRange}>{t.townHall.preview.levels(info.queuedLevel, next.level)}</span>
      </p>
      {next.effects.length > 0 && (
        <dl className={styles.effects} aria-label={t.townHall.preview.effectsLabel}>
          {next.effects.map((effect) => {
            const values = formatEffect(effect, language);
            return (
              <div key={effect.kind} className={styles.effect}>
                <dt>{t.townHall.preview.effect[effect.kind]}</dt>
                <dd>
                  {values.from !== null && (
                    <>
                      <span className={styles.from}>{values.from}</span>
                      <span aria-hidden="true"> → </span>
                      <span className="sr-only"> {t.townHall.preview.toValue} </span>
                    </>
                  )}
                  <strong>{values.to}</strong>
                </dd>
              </div>
            );
          })}
        </dl>
      )}
      {unlocks.length > 0 && <p className={styles.unlocks}>{t.townHall.preview.unlocks(unlocks.join(', '))}</p>}
      {next.effects.length === 0 && unlocks.length === 0 && (
        <p className={styles.unlocks}>{t.townHall.preview.noEffect}</p>
      )}
      <CostList
        className={styles.costs}
        label={t.townHall.costsLabel}
        timeLabel={t.townHall.buildTime}
        cost={next.cost}
        pop={next.pop}
        seconds={next.durationSeconds}
        stock={context.stock}
        freePop={context.freePop}
      />
      {/* the cost above is the price of the next free slot, a paid slot's surcharge included */}
      {next.surchargePercent > 0 && !context.queueFull && (
        <p className={styles.status}>{t.townHall.surchargeNote(next.surchargePercent)}</p>
      )}
      <p className={blocker === null ? styles.ready : styles.status}>
        {blocker === null ? t.townHall.preview.ready : blockerText(t, language, context.now, blocker)}
      </p>
    </div>
  );
}
