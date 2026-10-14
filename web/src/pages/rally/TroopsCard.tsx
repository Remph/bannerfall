import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useReturnStack, type Troops, type VillageRef } from '../../api/queries/troops';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Sheet } from '../../components/Sheet';
import { UnitSummary } from '../../components/UnitSummary';
import type { UnitType } from '../../game/gameData';
import { presentUnits, withoutZeros, type UnitMap } from '../../game/troops';
import { useT } from '../../i18n';
import styles from './RallyPage.module.css';
import { TroopOverview } from './TroopOverview';
import { UnitPicker } from './UnitPicker';
import { villageLabel } from './villageLabel';
import { VillageRefText } from './VillageRefText';

/** A stack whose units are about to walk home (the sheet's content). */
interface StackReturn {
  stackId: number;
  /** withdraw: own units from another village; sendBack: another village's support from here. */
  kind: 'withdraw' | 'sendBack';
  /** Where the units are (withdraw) or where they go (send back). */
  village: VillageRef;
  units: UnitMap;
}

/**
 * The troops of and in this village (04-api, GET …/troops): an overview of all of them (M33), then
 * supports of other villages standing here (can be sent back) and this village's units standing
 * elsewhere (can be withdrawn) – each all or in part.
 */
export function TroopsCard({ villageId, troops }: { villageId: number; troops: Troops }) {
  const t = useT();
  const [returning, setReturning] = useState<StackReturn | null>(null);
  return (
    <Card title={t.rally.troopsTitle}>
      <div className={styles.groups}>
        <section className={styles.group}>
          <h3 className={styles.groupTitle}>{t.rally.overviewTitle}</h3>
          <TroopOverview troops={troops} />
        </section>
        <section className={styles.group}>
          <h3 className={styles.groupTitle}>{t.rally.supportsHere}</h3>
          {troops.supportsHere.length === 0 ? (
            <p className={styles.muted}>{t.rally.noSupportsHere}</p>
          ) : (
            <ul className={styles.stackList}>
              {troops.supportsHere.map((stack) => (
                <StackLine
                  key={stack.stackId}
                  village={stack.homeVillage}
                  units={stack.units}
                  currentVillageId={villageId}
                  actionLabel={t.rally.sendBack}
                  onAction={() =>
                    setReturning({
                      stackId: stack.stackId,
                      kind: 'sendBack',
                      village: stack.homeVillage,
                      units: stack.units,
                    })
                  }
                />
              ))}
            </ul>
          )}
        </section>
        <section className={styles.group}>
          <h3 className={styles.groupTitle}>{t.rally.elsewhere}</h3>
          {troops.ownUnitsElsewhere.length === 0 ? (
            <p className={styles.muted}>{t.rally.noElsewhere}</p>
          ) : (
            <ul className={styles.stackList}>
              {troops.ownUnitsElsewhere.map((stack) => (
                <StackLine
                  key={stack.stackId}
                  village={stack.locationVillage}
                  units={stack.units}
                  currentVillageId={villageId}
                  actionLabel={t.rally.withdraw}
                  onAction={() =>
                    setReturning({
                      stackId: stack.stackId,
                      kind: 'withdraw',
                      village: stack.locationVillage,
                      units: stack.units,
                    })
                  }
                />
              ))}
            </ul>
          )}
        </section>
      </div>
      <Sheet
        open={returning !== null}
        onClose={() => setReturning(null)}
        title={returning?.kind === 'sendBack' ? t.rally.sendBackTitle : t.rally.withdrawTitle}
      >
        {returning !== null && (
          <StackReturnForm villageId={villageId} stack={returning} onDone={() => setReturning(null)} />
        )}
      </Sheet>
    </Card>
  );
}

interface StackLineProps {
  village: VillageRef;
  units: UnitMap;
  currentVillageId: number;
  actionLabel: string;
  onAction: () => void;
}

/** A stack: whose village (with owner), its units, and the button that sends them home. */
function StackLine({ village, units, currentVillageId, actionLabel, onAction }: StackLineProps) {
  const t = useT();
  return (
    <li className={styles.stack}>
      <span className={styles.stackText}>
        <span className={styles.moveTitle}>
          <VillageRefText village={village} currentVillageId={currentVillageId} />
          {village.playerName !== null && <span className={styles.player}> · {village.playerName}</span>}
        </span>
        <UnitSummary units={units} className={styles.moveUnits} />
      </span>
      <span className={styles.moveActions}>
        <Button type="button" variant="secondary" size="small" onClick={onAction}>
          {actionLabel}
          <span className="sr-only"> ({villageLabel(t, village)})</span>
        </Button>
      </span>
    </li>
  );
}

/**
 * How many of the stack's units walk home – all of them to start with. The server answers with a
 * RETURN that shows up in the incoming list of the units' home village.
 */
function StackReturnForm({ villageId, stack, onDone }: { villageId: number; stack: StackReturn; onDone: () => void }) {
  const t = useT();
  const [counts, setCounts] = useState<UnitMap>(() => withoutZeros(stack.units));
  const returnStack = useReturnStack(villageId);
  const units = withoutZeros(counts);
  const types = presentUnits(stack.units).map(([type]) => type);
  const label = villageLabel(t, stack.village);
  return (
    <div className={styles.confirm}>
      <p>{stack.kind === 'withdraw' ? t.rally.withdrawText(label) : t.rally.sendBackText(label)}</p>
      <UnitPicker
        types={types}
        available={stack.units}
        counts={counts}
        onChange={(type: UnitType, count: number) => setCounts((previous) => ({ ...previous, [type]: count }))}
        label={t.rally.returnCountLabel}
      />
      {returnStack.isError && <ErrorMessage>{errorMessage(t, returnStack.error)}</ErrorMessage>}
      <div className={styles.confirmActions}>
        <Button type="button" variant="secondary" onClick={onDone}>
          {t.rally.back}
        </Button>
        <Button
          type="button"
          disabled={presentUnits(units).length === 0 || returnStack.isPending}
          onClick={() => returnStack.mutate({ stackId: stack.stackId, units }, { onSuccess: onDone })}
        >
          {stack.kind === 'withdraw' ? t.rally.withdraw : t.rally.sendBack}
        </Button>
      </div>
    </div>
  );
}
