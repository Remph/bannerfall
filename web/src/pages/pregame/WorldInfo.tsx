import { useWorld, type World } from '../../api/queries/world';
import { errorMessage } from '../../api/errors';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatDecimal, formatNumber } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './Pregame.module.css';

/** The world settings every player may see (pre-game page; the "World settings" sheet of the menu). */
export function WorldInfo() {
  const { t } = useI18n();
  const world = useWorld();
  if (world.isError) {
    return <ErrorMessage>{errorMessage(t, world.error)}</ErrorMessage>;
  }
  if (world.data === undefined) {
    return <p>{t.common.loading}</p>;
  }
  return <WorldInfoList world={world.data} />;
}

function WorldInfoList({ world }: { world: World }) {
  const { t, language } = useI18n();
  const labels = t.worldInfo;
  const rows: [string, string][] = [
    [labels.speed, `× ${formatDecimal(world.speed, language)}`],
    [labels.unitSpeed, `× ${formatDecimal(world.unitSpeed, language)}`],
    [labels.mapSize, labels.mapSizeValue(world.mapSize)],
    [labels.startResources, labels.startResourcesValue(formatNumber(world.startResources, language))],
    [labels.beginnerProtection, labels.beginnerProtectionValue(world.beginnerProtectionHours)],
    [labels.nobleMaxDistance, labels.nobleMaxDistanceValue(world.nobleMaxDistance)],
    [labels.loyaltyRegen, labels.loyaltyRegenValue(formatDecimal(world.loyaltyRegenPerHour, language))],
    [labels.luck, labels.luckValue(`${formatDecimal(world.luckRange * 100, language)} %`)],
    [labels.buildQueue, labels.buildQueueValue(world.buildQueueSize, world.buildQueueMax)],
    [labels.recruitQueue, labels.recruitQueueValue(world.recruitQueueSize)],
    [labels.cancelWindow, labels.cancelWindowValue(formatNumber(Math.round(world.cancelWindowSeconds / 60), language))],
    [labels.barbarianGrowth, world.barbarianGrowthEnabled ? labels.yes : labels.no],
    [
      labels.barbarianWalls,
      world.barbarianWallsEnabled
        ? labels.barbarianWallsValue(
            world.barbarianWallStartDays,
            world.barbarianWallMaxLevel,
            world.barbarianWallsRebuild,
          )
        : labels.no,
    ],
    [labels.npcLords, labels.npcLordsValue(world.npcLordCount, t.admin.difficulty.names[world.npcDifficulty])],
    [labels.marketRatio, labels.marketRatioValue(formatDecimal(world.tradeMaxRatio, language))],
    [labels.guilds, world.guildsEnabled ? labels.guildsValue(world.guildMaxMembers) : labels.no],
    [labels.unitLevels, world.unitLevelsEnabled ? labels.unitLevelsValue : labels.no],
    [
      labels.paladin,
      world.paladinMaxSlots > 0 ? labels.paladinValue(world.paladinVillagesPerSlot, world.paladinMaxSlots) : labels.no,
    ],
    [labels.banners, world.bannersEnabled ? labels.bannersValue : labels.no],
  ];
  return (
    <dl className={styles.facts}>
      {rows.map(([label, value]) => (
        <div key={label} className={styles.fact}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}
