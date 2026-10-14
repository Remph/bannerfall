import type { ReactNode } from 'react';
import type {
  BannerReport,
  BattleReport,
  DefenderSide,
  LoyaltyReport,
  ScoutingReport,
} from '../../api/queries/reports';
import type { VillageRef } from '../../api/queries/troops';
import { Banner } from '../../components/Banner';
import { Card } from '../../components/Card';
import { Icon } from '../../components/Icon';
import { UnitSummary } from '../../components/UnitSummary';
import { UnitTable } from '../../components/UnitTable';
import { percentPoints } from '../../game/banners';
import { formatNumber, formatPercent, formatShare } from '../../game/format';
import { battleVerdict, builtBuildings, hasSupporters, unitRows, type ReportKind } from '../../game/reports';
import { presentUnits } from '../../game/troops';
import { sharedLevels, toLevelMap } from '../../game/unitLevels';
import type { BannerType } from '../../game/gameData';
import { useI18n, useT } from '../../i18n';
import { VillageRefText } from '../rally/VillageRefText';
import styles from './ReportsPage.module.css';

interface BattleDetailsProps {
  kind: ReportKind;
  battle: BattleReport;
  /** The current village (map links keep it); undefined without a village. */
  villageId: number | undefined;
}

/**
 * A battle as the reader may see it (02-game-rules §14): the verdict, both sides with their units
 * and losses, wall and catapult damage, the loot and what the scouts saw. What the attacker may not
 * know is null in his copy; the sections then say so or stay away.
 */
export function BattleDetails({ kind, battle, villageId }: BattleDetailsProps) {
  const { t, language } = useI18n();
  const { verdict, result } = battleVerdict(
    kind,
    battle.outcome,
    battle.scouting?.succeeded,
    (battle.attackerUnits.SCOUT ?? 0) > 0,
  );
  const defenderView = kind === 'DEFENSE';
  const scoutsOnly = battle.outcome === 'SCOUTS_ONLY';

  return (
    <>
      <p className={[styles.verdict, styles[result]].filter(Boolean).join(' ')}>{t.reports.verdicts[verdict]}</p>
      <div className={styles.columns}>
        <Card title={t.reports.attacker}>
          <div className={styles.cardBody}>
            <ReportVillage village={battle.attacker} villageId={villageId} />
            <UnitTable
              rows={unitRows(battle.attackerUnits, battle.attackerLosses)}
              caption={t.reports.attacker}
              countLabel={t.reports.sent}
              levels={toLevelMap(battle.attackerLevels)}
            />
            {(battle.luck !== null || battle.catapultTarget !== null) && (
              <dl className={styles.facts}>
                {battle.luck !== null && (
                  <div>
                    <dt>{t.reports.luck}</dt>
                    <dd>{formatPercent(battle.luck, language)}</dd>
                  </div>
                )}
                {battle.catapultTarget !== null && (
                  <div>
                    <dt>{t.reports.catapultTarget}</dt>
                    <dd>{t.buildings[battle.catapultTarget]}</dd>
                  </div>
                )}
              </dl>
            )}
          </div>
        </Card>
        <Card title={t.reports.defender}>
          <div className={styles.cardBody}>
            <ReportVillage village={battle.defender} villageId={villageId} />
            {battle.defenderSide === null ? (
              <p className={styles.muted}>{t.reports.nobodyCameBack}</p>
            ) : (
              <DefenderDetails battle={battle} side={battle.defenderSide} villageId={villageId} />
            )}
            {battle.loyalty !== null && <LoyaltyFacts loyalty={battle.loyalty} defenderView={defenderView} />}
          </div>
        </Card>
      </div>
      <div className={styles.columns}>
        {battle.outcome === 'ATTACKER_WON' && (
          <Card title={defenderView ? t.reports.plundered : t.reports.loot}>
            <div className={styles.cardBody}>
              {battle.loot.wood + battle.loot.clay + battle.loot.iron > 0 ? (
                <ResourceList amounts={battle.loot} />
              ) : (
                <p className={styles.muted}>{t.reports.nothingTaken}</p>
              )}
            </div>
          </Card>
        )}
        {/* the defenders' scouts-only verdict already says how the scouting went */}
        {battle.scouting !== null && !(defenderView && scoutsOnly) && (
          <ScoutingCard scouting={battle.scouting} defenderView={defenderView} />
        )}
        {battle.banners != null && <BannersCard banners={battle.banners} />}
      </div>
    </>
  );
}

/** The defending units, the wall and the catapult damage, and (defenders only) whose units they were. */
function DefenderDetails({
  battle,
  side,
  villageId,
}: {
  battle: BattleReport;
  side: DefenderSide;
  villageId: number | undefined;
}) {
  const t = useT();
  const rows = unitRows(side.units, side.losses);
  const catapults =
    battle.catapultTarget !== null && side.catapultLevelBefore !== null && side.catapultLevelAfter !== null
      ? { target: battle.catapultTarget, before: side.catapultLevelBefore, after: side.catapultLevelAfter }
      : undefined;
  return (
    <>
      {rows.length === 0 ? (
        <p className={styles.muted}>{t.reports.noDefenders}</p>
      ) : (
        <UnitTable
          rows={rows}
          caption={t.reports.defender}
          countLabel={t.reports.present}
          // defending units never die against scouts only (02 §7.1)
          showLosses={battle.outcome !== 'SCOUTS_ONLY'}
          levels={side.stacks.length > 0 ? sharedLevels(side.stacks) : toLevelMap(side.levels)}
        />
      )}
      {Object.keys(side.levels).length > 0 && <p className={styles.muted}>{t.reports.levelsScouted}</p>}
      {(side.wallBefore > 0 || catapults !== undefined) && (
        <dl className={styles.facts}>
          {side.wallBefore > 0 && (
            <div>
              <dt>{t.reports.wall}</dt>
              <dd>
                <LevelChange before={side.wallBefore} after={side.wallAfter} />
              </dd>
            </div>
          )}
          {catapults !== undefined && (
            <div>
              <dt>{t.buildings[catapults.target]}</dt>
              <dd>
                <LevelChange before={catapults.before} after={catapults.after} />
              </dd>
            </div>
          )}
        </dl>
      )}
      {hasSupporters(side.stacks, battle.defender.id) && (
        <div className={styles.group}>
          <h3 className={styles.groupTitle}>{t.reports.byVillage}</h3>
          <ul className={styles.stackList}>
            {side.stacks.map((stack) => (
              <li key={stack.home.id} className={styles.stack}>
                <ReportVillage village={stack.home} villageId={villageId} />
                <UnitSummary units={stack.units} levels={toLevelMap(stack.levels)} />
                <span className={styles.stackLosses}>
                  {presentUnits(stack.losses).length === 0 ? (
                    t.reports.noLosses
                  ) : (
                    <>
                      {t.reports.stackLosses} <UnitSummary units={stack.losses} className={styles.inlineUnits} />
                    </>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}

/** What the surviving nobles did to the loyalty (02-game-rules §10), and whether the village fell. */
function LoyaltyFacts({ loyalty, defenderView }: { loyalty: LoyaltyReport; defenderView: boolean }) {
  const t = useT();
  return (
    <>
      <dl className={styles.facts}>
        <div>
          <dt>{t.reports.loyalty}</dt>
          <dd>
            {loyalty.before === loyalty.after ? (
              // no noble hit it (M21): the attacker only learns the value
              <span>{loyalty.after}</span>
            ) : (
              <span className={styles.damaged}>{t.reports.loyaltyChange(loyalty.before, loyalty.after)}</span>
            )}
          </dd>
        </div>
      </dl>
      {loyalty.conquered && (
        <p className={defenderView ? `${styles.conquered} ${styles.lost}` : styles.conquered}>
          {defenderView ? t.reports.lostInBattle : t.reports.conqueredInBattle}
        </p>
      )}
    </>
  );
}

/** What the attacking scouts found (attacker), or whether the enemy scouts got through (defenders). */
function ScoutingCard({ scouting, defenderView }: { scouting: ScoutingReport; defenderView: boolean }) {
  const { t, language } = useI18n();
  let content: ReactNode;
  if (defenderView) {
    content = (
      <p className={styles.muted}>
        {scouting.succeeded ? t.reports.verdicts.enemyScoutsThrough : t.reports.verdicts.enemyScoutsCaught}
      </p>
    );
  } else if (!scouting.succeeded) {
    content = <p className={styles.muted}>{t.reports.scoutsFailed}</p>;
  } else {
    const buildings = scouting.buildings === null ? [] : builtBuildings(scouting.buildings);
    content = (
      <>
        {scouting.resources !== null && (
          <div className={styles.group}>
            <h3 className={styles.groupTitle}>{t.reports.scoutedResources}</h3>
            <ResourceList amounts={scouting.resources} />
          </div>
        )}
        {scouting.banner != null && (
          <div className={styles.group}>
            <h3 className={styles.groupTitle}>{t.reports.scoutedBanner}</h3>
            <p className={styles.scoutedBanner}>
              <Banner type={scouting.banner.type} tier={scouting.banner.tier} size={20} />
              {t.banners.types[scouting.banner.type]}, {t.banners.tier(scouting.banner.tier)}
            </p>
          </div>
        )}
        {buildings.length > 0 && (
          <div className={styles.group}>
            <h3 className={styles.groupTitle}>{t.reports.scoutedBuildings}</h3>
            <ul className={styles.buildingList}>
              {buildings.map(([type, level]) => (
                <li key={type}>
                  <span className={styles.wellIcon}>
                    <Icon name={type} />
                  </span>
                  <span className={styles.buildingName}>{t.buildings[type]}</span>
                  <span className={styles.buildingLevel}>{formatNumber(level, language)}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
        <p className={styles.muted}>{t.reports.scoutedTroops}</p>
      </>
    );
  }
  return (
    <Card title={t.reports.scouting}>
      <div className={styles.cardBody}>{content}</div>
    </Card>
  );
}

/**
 * What banners did in the battle (M32b, 02-game-rules §21): the War and Bulwark bonuses the reader may
 * know, the banner the reader's side found, what the attacker's thieves did, or (defenders) the banner
 * they stole. The server leaves out what the reader's copy doesn't hold.
 */
function BannersCard({ banners }: { banners: BannerReport }) {
  const { t, language } = useI18n();
  const percent = (fraction: number) => t.reports.bonus(`${formatNumber(percentPoints(fraction), language)} %`);
  const name = (banner: { type: BannerType; tier: number }) =>
    t.banners.label(t.banners.types[banner.type], banner.tier);
  const theft = banners.theft;
  return (
    <Card title={t.reports.bannersTitle}>
      <div className={styles.cardBody}>
        {(banners.attackBonus !== null || banners.defenceBonus !== null) && (
          <dl className={styles.facts}>
            {banners.attackBonus !== null && (
              <div>
                <dt>{t.reports.warBonus}</dt>
                <dd>{percent(banners.attackBonus)}</dd>
              </div>
            )}
            {banners.defenceBonus !== null && (
              <div>
                <dt>{t.reports.bulwarkBonus}</dt>
                <dd>{percent(banners.defenceBonus)}</dd>
              </div>
            )}
          </dl>
        )}
        {banners.found !== null && (
          <div className={styles.group}>
            <h3 className={styles.groupTitle}>{t.reports.bannerFound}</h3>
            <p className={`${styles.scoutedBanner} ${styles.bannerWon}`}>
              <Banner type={banners.found.type} tier={banners.found.tier} size={20} />
              {name(banners.found)}
            </p>
          </div>
        )}
        {theft !== null && (
          <>
            <dl className={styles.facts}>
              <div>
                <dt>{t.reports.thieves}</dt>
                <dd>
                  {t.reports.thievesCount(formatNumber(theft.sent, language), formatNumber(theft.survived, language))}
                </dd>
              </div>
              {theft.chance !== null && (
                <div>
                  <dt>{t.reports.theftChance}</dt>
                  {/* one decimal: formatNumber would floor a 12.5 % chance to 12 % */}
                  <dd>{formatShare(percentPoints(theft.chance), language)}</dd>
                </div>
              )}
            </dl>
            <p
              className={[styles.scoutedBanner, theft.result === 'STOLEN' ? styles.bannerWon : styles.muted].join(' ')}
            >
              {theft.banner !== null && <Banner type={theft.banner.type} tier={theft.banner.tier} size={20} />}
              {t.reports.theft[theft.result](theft.banner === null ? '' : name(theft.banner))}
            </p>
          </>
        )}
        {banners.stolen !== null && (
          <p className={`${styles.scoutedBanner} ${styles.bannerLost}`}>
            <Banner type={banners.stolen.type} tier={banners.stolen.tier} size={20} />
            {t.reports.bannerStolen(name(banners.stolen))}
          </p>
        )}
      </div>
    </Card>
  );
}

/** "Lv 5 → 4", or just "Lv 5" when nothing changed. */
function LevelChange({ before, after }: { before: number; after: number }) {
  const t = useT();
  return before === after ? (
    t.reports.level(before)
  ) : (
    <span className={styles.damaged}>{t.reports.levelChange(before, after)}</span>
  );
}

/** Wood, clay and iron with their icons. */
export function ResourceList({ amounts }: { amounts: { wood: number; clay: number; iron: number } }) {
  const { t, language } = useI18n();
  return (
    <ul className={styles.resources}>
      {(['wood', 'clay', 'iron'] as const).map((resource) => (
        <li key={resource} title={t.resources[resource]}>
          <Icon name={resource} className={styles.resourceIcon} />
          <span className="sr-only">{t.resources[resource]}: </span>
          {formatNumber(amounts[resource], language)}
        </li>
      ))}
    </ul>
  );
}

/** A village of the report: name, coordinates (a link to the map) and owner. */
export function ReportVillage({ village, villageId }: { village: VillageRef; villageId: number | undefined }) {
  return (
    <p className={styles.village}>
      <VillageRefText village={village} currentVillageId={villageId ?? 0} link={villageId !== undefined} />
      {village.playerName !== null && <span className={styles.player}> · {village.playerName}</span>}
    </p>
  );
}
