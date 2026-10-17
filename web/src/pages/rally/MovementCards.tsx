import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCancelMovement, type IncomingMovement, type Movement, type VillageRef } from '../../api/queries/troops';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { Countdown } from '../../components/Countdown';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon, type IconName } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { UnitSummary } from '../../components/UnitSummary';
import { formatWhenExact } from '../../game/format';
import { useGameNow } from '../../game/serverTime';
import { canCancel, travelProgress, type MovementKind, type UnitMap } from '../../game/troops';
import { useI18n, useT } from '../../i18n';
import styles from './RallyPage.module.css';
import { mapPath } from './rallyPaths';
import { villageLabel } from './villageLabel';
import { VillageRefText } from './VillageRefText';

const movementIcons: Record<MovementKind, IconName> = { ATTACK: 'troops', SUPPORT: 'shield', RETURN: 'returning' };

/**
 * Attacks and supports this village has sent that are still on the way, next first. A command can
 * be cancelled for a short while after it left (the button shows how long), after a confirmation;
 * the troops then turn around.
 */
export function OutgoingCard({ villageId, movements }: { villageId: number; movements: readonly Movement[] }) {
  const t = useT();
  const now = useGameNow();
  const cancel = useCancelMovement(villageId);
  const [confirming, setConfirming] = useState<Movement | null>(null);
  // The error itself, not its text: the message is translated while rendering (language switch).
  const [error, setError] = useState<Error | null>(null);

  function cancelMovement(movement: Movement) {
    setError(null);
    cancel.mutate(movement.id, {
      onSuccess: () => setConfirming(null),
      onError: (failure) => {
        setConfirming(null);
        setError(failure);
      },
    });
  }

  return (
    <Card title={t.rally.outgoingTitle} id="outgoing">
      {movements.length === 0 ? (
        <p className={styles.empty}>{t.rally.noOutgoing}</p>
      ) : (
        <ul className={styles.moveList}>
          {movements.map((movement) => (
            <MovementLine
              key={movement.id}
              kind={movement.kind}
              label={t.rally.outgoing[movement.kind]}
              village={movement.targetVillage}
              player={movement.targetVillage.playerName}
              departAt={movement.departAt}
              arriveAt={movement.arriveAt}
              units={movement.units}
              currentVillageId={villageId}
              action={
                // disappears on the second the window closes (cancellableUntil is exclusive)
                movement.cancellableUntil !== null && canCancel(movement, now) ? (
                  <Button
                    type="button"
                    variant="danger"
                    size="small"
                    disabled={cancel.isPending}
                    onClick={() => setConfirming(movement)}
                  >
                    {t.rally.cancel}
                    <span className="sr-only">
                      {' '}
                      ({t.rally.outgoing[movement.kind]} {villageLabel(t, movement.targetVillage)})
                    </span>{' '}
                    <Countdown until={movement.cancellableUntil} />
                  </Button>
                ) : undefined
              }
            />
          ))}
        </ul>
      )}
      {error !== null && (
        <div className={styles.cardError}>
          <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>
        </div>
      )}
      {/* Closes by itself when the cancel window ends meanwhile (the server would answer
          CANCEL_WINDOW_OVER). Closing calls onClose, which clears `confirming`. */}
      <Sheet
        open={confirming !== null && canCancel(confirming, now)}
        onClose={() => setConfirming(null)}
        title={t.rally.cancelTitle}
      >
        {confirming !== null && (
          <CancelConfirm
            movement={confirming}
            pending={cancel.isPending}
            onKeep={() => setConfirming(null)}
            onConfirm={() => cancelMovement(confirming)}
          />
        )}
      </Sheet>
    </Card>
  );
}

interface CancelConfirmProps {
  movement: Movement;
  pending: boolean;
  onKeep: () => void;
  onConfirm: () => void;
}

/**
 * The cancel sheet's content: which command, when the troops would be back, how long it can still be
 * cancelled. Also asked on the map, when a picked line is cancelled there.
 */
export function CancelConfirm({ movement, pending, onKeep, onConfirm }: CancelConfirmProps) {
  const { t, language } = useI18n();
  const now = useGameNow();
  // they walk back as long as they have been on the way (02-game-rules §6)
  const backAt = now + Math.max(0, now - Date.parse(movement.departAt));
  return (
    <div className={styles.confirm}>
      <p className={styles.confirmTarget}>
        {t.rally.outgoing[movement.kind]} {villageLabel(t, movement.targetVillage)}
      </p>
      <UnitSummary units={movement.units} />
      <p>{t.rally.cancelReturn(formatWhenExact(backAt, now, language))}</p>
      {movement.cancellableUntil !== null && (
        <p className={styles.muted}>
          {t.rally.cancelLeft} <Countdown until={movement.cancellableUntil} className={styles.countdown} />
        </p>
      )}
      <div className={styles.confirmActions}>
        <Button type="button" variant="secondary" onClick={onKeep}>
          {t.buildQueue.keep}
        </Button>
        <Button type="button" disabled={pending} onClick={onConfirm}>
          {t.rally.confirmCancel}
        </Button>
      </div>
    </div>
  );
}

/**
 * Everything on its way to this village, next first: attacks (red, units hidden), supports, and
 * the village's own troops coming home. That another player's attack hides its troops and start is
 * said once above the list (1.3); each such attack links to the attacker on the map instead.
 */
export function IncomingCard({ villageId, incoming }: { villageId: number; incoming: readonly IncomingMovement[] }) {
  const t = useT();
  // the server hides the units of every attack except the player's own (a later wave on a village
  // he conquered meanwhile, it arrives like a support)
  const hidden = (movement: IncomingMovement) => movement.kind === 'ATTACK' && movement.units === null;
  return (
    <Card title={t.rally.incomingTitle} id="incoming">
      {incoming.some(hidden) && <p className={styles.incomingNote}>{t.rally.attacksHidden}</p>}
      {incoming.length === 0 ? (
        <p className={styles.empty}>{t.rally.noIncoming}</p>
      ) : (
        <ul className={styles.moveList}>
          {incoming.map((movement) => (
            <MovementLine
              key={movement.id}
              kind={movement.kind}
              label={t.rally.incoming[movement.kind]}
              village={movement.sourceVillage}
              player={movement.sourcePlayerName}
              // for the player's own movements and every support; someone else's attack hides its start
              departAt={movement.departAt ?? undefined}
              arriveAt={movement.arriveAt}
              units={movement.units}
              currentVillageId={villageId}
              hostile={hidden(movement)}
            />
          ))}
        </ul>
      )}
    </Card>
  );
}

interface MovementLineProps {
  kind: MovementKind;
  /** "Attack on", "Support from" …: followed by the village. */
  label: string;
  village: VillageRef;
  player: string | null;
  /** Given for the player's own movements: a bar shows how far they have come. */
  departAt?: string;
  arriveAt: string;
  /** null: hidden (incoming attacks); a link to the other village on the map takes their place. */
  units: UnitMap | null;
  currentVillageId: number;
  hostile?: boolean;
  action?: ReactNode;
}

const barStyles: Record<MovementKind, string | undefined> = {
  ATTACK: styles.barAttack,
  SUPPORT: styles.barSupport,
  RETURN: styles.barReturn,
};

/** One movement: what and where, countdown and arrival time (to the second), progress, the units. */
function MovementLine(props: MovementLineProps) {
  const { kind, label, village, player, departAt, arriveAt, units, currentVillageId, hostile = false, action } = props;
  const { t, language } = useI18n();
  const now = useGameNow();
  return (
    <li className={hostile ? `${styles.move} ${styles.hostile}` : styles.move}>
      <span className={styles.moveIcon}>
        <Icon name={movementIcons[kind]} />
      </span>
      <span className={styles.moveText}>
        <span className={styles.moveTitle}>
          {label} <VillageRefText village={village} currentVillageId={currentVillageId} />
          {player !== null && <span className={styles.player}> · {player}</span>}
        </span>
        <span className={styles.moveTime}>
          <Countdown until={arriveAt} className={styles.countdown} />
          {' · '}
          {t.rally.arrivesAt(formatWhenExact(Date.parse(arriveAt), now, language))}
        </span>
        {departAt !== undefined && (
          <span className={`${styles.moveBar} ${barStyles[kind] ?? ''}`} aria-hidden="true">
            <span
              className={styles.moveFill}
              style={{ transform: `scaleX(${travelProgress({ departAt, arriveAt }, now)})` }}
            />
          </span>
        )}
        {units === null ? (
          <Link className={styles.onMap} to={mapPath(currentVillageId, village)}>
            {t.rally.onMap} <span className="sr-only">({villageLabel(t, village)})</span>
            <span aria-hidden="true"> ›</span>
          </Link>
        ) : (
          <UnitSummary units={units} className={styles.moveUnits} />
        )}
      </span>
      {action !== undefined && <span className={styles.moveActions}>{action}</span>}
    </li>
  );
}
