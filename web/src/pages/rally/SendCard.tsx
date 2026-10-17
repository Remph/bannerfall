import { useState } from 'react';
import { useSearchParams } from 'react-router';
import type { CommandRequest, Movement, TrainRequest } from '../../api/queries/troops';
import type { Village } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { IconButton } from '../../components/IconButton';
import { Sheet } from '../../components/Sheet';
import { TextField } from '../../components/TextField';
import { isOnMap, parseCoords } from '../../game/coords';
import { formatWhenExact } from '../../game/format';
import type { UnitType } from '../../game/gameData';
import { useGameNow } from '../../game/serverTime';
import { UnitSummary } from '../../components/UnitSummary';
import {
  MAX_TRAIN_ATTACKS,
  capCounts,
  leftForTrain,
  presentUnits,
  sendableUnits,
  trainAttacks,
  withoutZeros,
  type CommandKind,
  type UnitMap,
} from '../../game/troops';
import { useI18n, useT } from '../../i18n';
import { CommandConfirm } from './CommandConfirm';
import { TrainConfirm } from './TrainConfirm';
import styles from './RallyPage.module.css';
import { targetFromSearch } from './rallyPaths';
import { UnitPicker } from './UnitPicker';
import { useTargetVillage } from './useTargetVillage';
import { villageLabel } from './villageLabel';

interface SendCardProps {
  village: Village;
  /** The units at home (from the troops overview). */
  home: UnitMap;
}

/** What the last send did, for the message under the buttons. */
interface Sent {
  kind: CommandKind;
  target: string;
  arriveAt: string;
  /** How many attacks: more than 1 for a train. */
  count: number;
}

/** What the confirmation sheet shows: one command, or an attack train (M37). */
type ToConfirm = { type: 'command'; request: CommandRequest } | { type: 'train'; request: TrainRequest };

/**
 * Sending troops (05-frontend §3): target field (prefilled from `?x=&y=`, the map's attack and
 * support buttons), how many of each unit, Attack / Support. Both buttons first open a sheet with
 * the server's preview (distance, travel time, arrival, broken rules); only its Confirm sends.
 *
 * Attack train (M37, 02-game-rules §6.1): "Add to train" lines up the picked units as an attack and
 * keeps what still fits in the form, so the next noble attack is one more tap; Attack then sends the
 * attacks lined up plus the picked units as the last one, all with one confirm.
 */
export function SendCard({ village, home }: SendCardProps) {
  const t = useT();
  const [searchParams] = useSearchParams();
  // useState with a function: the URL is only read for the first render
  const [target, setTarget] = useState(() => targetFromSearch(searchParams));
  const [targetError, setTargetError] = useState<string | undefined>();
  const [counts, setCounts] = useState<UnitMap>({});
  const [unitsError, setUnitsError] = useState(false);
  // the confirmation sheet is open while there is a command to confirm
  const [command, setCommand] = useState<ToConfirm | null>(null);
  // the attacks lined up for a train, in the order they land
  const [train, setTrain] = useState<UnitMap[]>([]);
  const [sent, setSent] = useState<Sent | null>(null);
  const mapSize = useWorld().data?.mapSize;

  const parsed = parseCoords(target);
  const field = parsed !== undefined && (mapSize === undefined || isOnMap(parsed, mapSize)) ? parsed : undefined;
  const targetVillage = useTargetVillage(field, village);
  // what the next attack can take: the units at home less the attacks lined up
  const available = leftForTrain(home, train);
  const units = withoutZeros(counts);
  const picked = presentUnits(units).length > 0;

  function changeCount(type: UnitType, count: number) {
    // a new object: React only re-renders when the state is a different object
    setCounts((previous) => ({ ...previous, [type]: count }));
    setUnitsError(false);
    setSent(null);
  }

  function addToTrain() {
    if (!picked || train.length >= MAX_TRAIN_ATTACKS) {
      return;
    }
    const lined = [...train, units];
    setTrain(lined);
    setCounts(capCounts(units, leftForTrain(home, lined)));
    setUnitsError(false);
    setSent(null);
  }

  function removeFromTrain(index: number) {
    setTrain((previous) => previous.filter((_, i) => i !== index));
    setSent(null);
  }

  function open(kind: CommandKind) {
    setSent(null);
    const attacks = kind === 'ATTACK' ? trainAttacks(train, units) : [];
    // with a train lined up, Attack has something to send even when nothing is picked
    const noUnits = kind === 'ATTACK' ? attacks.length === 0 : !picked;
    setUnitsError(noUnits);
    let error: string | undefined;
    if (parsed === undefined) {
      error = t.map.invalidCoords;
    } else if (field === undefined && mapSize !== undefined) {
      error = t.map.offMap(mapSize - 1);
    } else if (targetVillage.kind === 'none') {
      error = t.errors.TARGET_NOT_FOUND;
    }
    setTargetError(error);
    if (error === undefined && !noUnits && parsed !== undefined) {
      // a train that comes down to one attack goes out as a plain attack ("Send 1 attacks?" otherwise)
      if (kind === 'ATTACK' && attacks.length > 1) {
        setCommand({
          type: 'train',
          request: { target: parsed, attacks: attacks.map((attack) => ({ units: attack, catapultTarget: null })) },
        });
      } else {
        setCommand({
          type: 'command',
          request: { kind, target: parsed, units: attacks[0] ?? units, catapultTarget: null },
        });
      }
    }
  }

  function onSent(movements: Movement[]) {
    setCommand(null);
    setCounts({});
    setTrain([]);
    const first = movements[0];
    if (first !== undefined && first.kind !== 'RETURN') {
      setSent({
        kind: first.kind,
        target: villageLabel(t, first.targetVillage),
        arriveAt: first.arriveAt,
        count: movements.length,
      });
    }
  }

  let hint: string;
  switch (targetVillage.kind) {
    case 'unknown':
      hint = t.rally.targetHint;
      break;
    case 'loading':
      hint = t.rally.targetLoading;
      break;
    case 'none':
      hint = t.rally.noVillageThere;
      break;
    case 'found':
      hint = targetVillage.text;
  }

  const rows = sendableUnits(available, counts);
  const trainFull = train.length >= MAX_TRAIN_ATTACKS;
  return (
    <Card title={t.rally.sendTitle}>
      <div className={styles.body}>
        <div className={styles.target}>
          <TextField
            label={t.rally.target}
            value={target}
            onChange={(event) => {
              setTarget(event.target.value);
              setTargetError(undefined);
              setSent(null);
            }}
            // phone number keyboards have no "|"; "48.52" works too (parseCoords)
            inputMode="decimal"
            autoComplete="off"
            spellCheck={false}
            placeholder="48|52"
            hint={hint}
            error={targetError}
          />
        </div>
        {train.length > 0 && (
          <section className={styles.train} aria-label={t.rally.trainTitle}>
            <p className={styles.trainHead}>
              <span className={styles.trainTitle}>{t.rally.trainTitle}</span>
              <span className={styles.muted}>{t.rally.trainCount(train.length, MAX_TRAIN_ATTACKS)}</span>
            </p>
            <ol className={styles.trainList}>
              {train.map((attack, index) => (
                // removing one renumbers the rest: the position is what the player sees
                <li key={index} className={styles.trainRow}>
                  <span className={styles.trainNumber}>{t.rally.trainAttack(index + 1)}</span>
                  <UnitSummary units={attack} />
                  <IconButton
                    icon="close"
                    label={t.rally.trainRemove(index + 1)}
                    onClick={() => removeFromTrain(index)}
                  />
                </li>
              ))}
            </ol>
            <p className={styles.muted}>{trainFull ? t.rally.trainFull : t.rally.trainHint}</p>
          </section>
        )}
        {rows.length === 0 ? (
          <p className={styles.muted}>{t.rally.noTroops}</p>
        ) : (
          <div className={styles.units}>
            <div className={styles.quick}>
              <Button
                type="button"
                variant="secondary"
                size="small"
                onClick={() => {
                  setCounts(available);
                  setUnitsError(false);
                  setSent(null);
                }}
              >
                {t.rally.allTroops}
              </Button>
              <Button type="button" variant="secondary" size="small" onClick={() => setCounts({})}>
                {t.rally.clear}
              </Button>
              <Button
                type="button"
                variant="secondary"
                size="small"
                className={styles.addToTrain}
                disabled={!picked || trainFull}
                onClick={addToTrain}
              >
                <Icon name="plus" className={styles.buttonIcon} />
                {t.rally.addToTrain}
              </Button>
            </div>
            <UnitPicker
              types={rows}
              available={available}
              counts={counts}
              onChange={changeCount}
              label={t.rally.countLabel}
            />
          </div>
        )}
        {unitsError && <ErrorMessage>{t.errors.EMPTY_COMMAND}</ErrorMessage>}
        {/* phones: sticks to the bottom of the screen (above the tab bar) while the unit list scrolls by */}
        <div className={styles.sendFooter}>
          <div className={styles.sendActions}>
            {/* a train holds attacks only */}
            <Button type="button" variant="secondary" disabled={train.length > 0} onClick={() => open('SUPPORT')}>
              <Icon name="shield" className={styles.buttonIcon} />
              {t.rally.support}
            </Button>
            <Button type="button" variant="attack" onClick={() => open('ATTACK')}>
              <Icon name="troops" className={styles.buttonIcon} />
              {train.length > 0 ? t.rally.attackTrain(trainAttacks(train, units).length) : t.rally.attack}
            </Button>
          </div>
          <SentMessage sent={sent} />
        </div>
      </div>
      <Sheet
        open={command !== null}
        onClose={() => setCommand(null)}
        title={
          command === null
            ? t.rally.sendTitle
            : command.type === 'train'
              ? t.rally.trainPreviewTitle(command.request.attacks.length)
              : t.rally.previewTitle[command.request.kind]
        }
      >
        {command?.type === 'command' && (
          <CommandConfirm
            villageId={village.id}
            command={command.request}
            onSent={(movement) => onSent([movement])}
            onBack={() => setCommand(null)}
          />
        )}
        {command?.type === 'train' && (
          <TrainConfirm
            villageId={village.id}
            train={command.request}
            onSent={onSent}
            onBack={() => setCommand(null)}
          />
        )}
      </Sheet>
    </Card>
  );
}

/**
 * "Attack on Hilltop (48|52) sent – arrives at 21:14:05." after a send, until the form is changed.
 * Always rendered (empty without a message): role="status" makes screen readers announce new text
 * without moving the focus, which works best on an element that is already there.
 */
function SentMessage({ sent }: { sent: Sent | null }) {
  const { t, language } = useI18n();
  const now = useGameNow(); // here, not in SendCard: only this line re-renders every second
  return (
    <p role="status" className={styles.sent}>
      {sent !== null &&
        (sent.count > 1
          ? t.rally.sentTrain(sent.count, sent.target, formatWhenExact(Date.parse(sent.arriveAt), now, language))
          : t.rally.sent[sent.kind](sent.target, formatWhenExact(Date.parse(sent.arriveAt), now, language)))}
    </p>
  );
}
