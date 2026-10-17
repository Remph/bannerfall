import type { SimulatedBattle, Simulation } from '../../api/queries/simulator';
import { Card } from '../../components/Card';
import { UnitTable } from '../../components/UnitTable';
import { percentPoints } from '../../game/banners';
import { formatNumber, formatPercent, formatShare } from '../../game/format';
import { unitRows } from '../../game/reports';
import type { SimulatorBody } from '../../game/simulator';
import type { UnitMap } from '../../game/troops';
import { toLevelMap } from '../../game/unitLevels';
import { useI18n } from '../../i18n';
import styles from './Simulator.module.css';

interface SimulatorResultProps {
  simulation: Simulation;
  /** What was asked: the unit tables show the numbers of that request, not of the form as it is now. */
  request: SimulatorBody;
}

/**
 * The simulated battle with the worst luck, none and the best luck (02-game-rules §20): per
 * scenario the verdict, the strengths, wall and catapult damage, scouting, the carrying capacity,
 * what the nobles do to the loyalty, and both sides' units with losses.
 */
export function SimulatorResult({ simulation, request }: SimulatorResultProps) {
  const { t } = useI18n();
  return (
    <section className={styles.results} aria-labelledby="simulator-result">
      <h2 id="simulator-result">{t.simulator.resultsTitle}</h2>
      <p className={styles.muted}>{t.simulator.resultsHint}</p>
      <BannerBonuses simulation={simulation} />
      <div className={styles.scenarios}>
        {simulation.scenarios.map((scenario) => (
          <Scenario key={scenario.luck} scenario={scenario} request={request} />
        ))}
      </div>
    </section>
  );
}

/** The banners the battles were fought with (M32c; Plunder and Oath since D4); nothing without. */
function BannerBonuses({ simulation }: { simulation: Simulation }) {
  const { t, language } = useI18n();
  const s = t.simulator;
  const percent = (fraction: number, sign = '+') => `${sign}${formatShare(percentPoints(fraction), language)}`;
  if (
    simulation.attackBonus <= 0 &&
    simulation.defenceBonus <= 0 &&
    simulation.plunderBonus <= 0 &&
    simulation.oathBonus <= 0
  ) {
    return null;
  }
  return (
    <dl className={styles.facts}>
      {simulation.attackBonus > 0 && (
        <div>
          <dt>{s.warBonus}</dt>
          <dd>{percent(simulation.attackBonus)}</dd>
        </div>
      )}
      {simulation.plunderBonus > 0 && (
        <div>
          <dt>{s.plunderBonus}</dt>
          <dd>{percent(simulation.plunderBonus)}</dd>
        </div>
      )}
      {simulation.defenceBonus > 0 && (
        <div>
          <dt>{s.bulwarkBonus}</dt>
          <dd>{percent(simulation.defenceBonus)}</dd>
        </div>
      )}
      {simulation.oathBonus > 0 && (
        <div>
          <dt>{s.oathBonus}</dt>
          <dd>{percent(simulation.oathBonus, '−')}</dd>
        </div>
      )}
    </dl>
  );
}

function luckTitle(luck: number): 'worst' | 'none' | 'best' {
  return luck < 0 ? 'worst' : luck > 0 ? 'best' : 'none';
}

function Scenario({ scenario, request }: { scenario: SimulatedBattle; request: SimulatorBody }) {
  const { t, language } = useI18n();
  const s = t.simulator;
  const title = luckTitle(scenario.luck);
  const number = (value: number) => formatNumber(value, language);
  const strength = (value: number) => formatNumber(Math.round(value), language);
  const result = scenario.outcome === 'ATTACKER_WON' ? 'won' : scenario.outcome === 'DEFENDER_WON' ? 'lost' : '';
  const catapultTarget = scenario.catapultTarget;
  // the wall is its own row; catapults that aim at it are in that row already
  const aimsAtOther = catapultTarget !== null && catapultTarget !== 'WALL';
  const loyalty = scenario.loyalty;
  return (
    <Card title={`${s.luckTitle[title]}${scenario.luck === 0 ? '' : ` (${formatPercent(scenario.luck, language)})`}`}>
      <div className={styles.body}>
        <p className={[styles.verdict, result && styles[result]].filter(Boolean).join(' ')}>
          {s.verdicts[scenario.outcome]}
        </p>
        <dl className={styles.facts}>
          {scenario.attack !== null && scenario.defense !== null && (
            <>
              <div>
                <dt>{s.attackStrength}</dt>
                <dd>{strength(scenario.attack)}</dd>
              </div>
              <div>
                <dt>{s.defenseStrength}</dt>
                <dd>{strength(scenario.defense)}</dd>
              </div>
            </>
          )}
          {scenario.effectiveWall !== null && scenario.effectiveWall !== scenario.wallBefore && (
            <div>
              <dt>{s.wallInFight}</dt>
              <dd>{t.reports.level(scenario.effectiveWall)}</dd>
            </div>
          )}
          {(scenario.wallBefore > 0 || scenario.wallAfter !== scenario.wallBefore) && (
            <div>
              <dt>{t.reports.wall}</dt>
              <dd className={scenario.wallAfter < scenario.wallBefore ? styles.damaged : undefined}>
                {t.reports.levelChange(scenario.wallBefore, scenario.wallAfter)}
              </dd>
            </div>
          )}
          {aimsAtOther && scenario.catapultTargetLevelBefore !== null && scenario.catapultTargetLevelAfter !== null && (
            <div>
              <dt>{t.buildings[catapultTarget]}</dt>
              <dd
                className={
                  scenario.catapultTargetLevelAfter < scenario.catapultTargetLevelBefore ? styles.damaged : undefined
                }
              >
                {t.reports.levelChange(scenario.catapultTargetLevelBefore, scenario.catapultTargetLevelAfter)}
              </dd>
            </div>
          )}
          {scenario.scouting !== null && (
            <>
              <div>
                <dt>{s.scoutsSent}</dt>
                <dd>{number(scenario.scouting.sent)}</dd>
              </div>
              <div>
                <dt>{s.scoutsDefending}</dt>
                <dd>{number(scenario.scouting.defending)}</dd>
              </div>
              <div>
                <dt>{s.scoutsLost}</dt>
                <dd>{number(scenario.scouting.lost)}</dd>
              </div>
              <div>
                <dt>{scenario.scouting.succeeded ? s.scoutsSucceeded : s.scoutsFailed}</dt>
                <dd>{scenario.scouting.succeeded ? s.yes : s.no}</dd>
              </div>
            </>
          )}
          {scenario.lootCapacity > 0 && (
            <div>
              <dt>{s.lootCapacity}</dt>
              <dd>{number(scenario.lootCapacity)}</dd>
            </div>
          )}
          {scenario.theft !== null && (
            <>
              <div>
                <dt>{s.thievesSurvive}</dt>
                <dd>{s.ofSent(number(scenario.theft.survived), number(scenario.theft.sent))}</dd>
              </div>
              <div>
                <dt>{s.theftChance}</dt>
                {/* one decimal: formatNumber would floor a 12.5 % chance to 12 % */}
                <dd>{formatShare(percentPoints(scenario.theft.chance), language)}</dd>
              </div>
            </>
          )}
          {loyalty !== null && (
            <div>
              <dt>{s.loyaltyOutlook}</dt>
              <dd>
                {s.loyaltyRange(
                  loyalty.nobles,
                  number(loyalty.before),
                  number(loyalty.highest),
                  number(loyalty.lowest),
                )}
              </dd>
            </div>
          )}
        </dl>
        {loyalty !== null && <p className={styles.notice}>{s.conquest[loyalty.conquered]}</p>}
        {scenario.theft !== null && scenario.theft.chance > 0 && <p className={styles.muted}>{s.theftHint}</p>}
        <h3 className={styles.tableTitle}>{s.attackerTable}</h3>
        <UnitTable
          rows={unitRows(request.attacker as UnitMap, scenario.attackerLosses as UnitMap)}
          caption={s.attackerTable}
          countLabel={t.reports.sent}
          levels={toLevelMap(request.attackerLevels)}
        />
        <h3 className={styles.tableTitle}>{s.defenderTable}</h3>
        {Object.keys(request.defender).length === 0 ? (
          <p className={styles.muted}>{t.reports.noDefenders}</p>
        ) : (
          <UnitTable
            rows={unitRows(request.defender as UnitMap, scenario.defenderLosses as UnitMap)}
            caption={s.defenderTable}
            countLabel={t.reports.present}
            // defending units never die against scouts only (02 §7.1)
            showLosses={scenario.outcome !== 'SCOUTS_ONLY'}
            levels={toLevelMap(request.defenderLevels)}
          />
        )}
      </div>
    </Card>
  );
}
