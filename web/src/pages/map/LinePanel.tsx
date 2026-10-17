import { useEffect, useEffectEvent, useId, useRef, useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import type { MapMovementLine } from '../../api/queries/map';
import { useCancelMovement, useTroops, type VillageRef } from '../../api/queries/troops';
import { Button } from '../../components/Button';
import { Countdown } from '../../components/Countdown';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { IconButton } from '../../components/IconButton';
import { Sheet } from '../../components/Sheet';
import { UnitSummary } from '../../components/UnitSummary';
import { formatCoords, formatDecimal, formatWhenExact } from '../../game/format';
import { lineFieldsLeft, lineHostile, lineKind, lineProgress } from '../../game/mapLines';
import type { Cover } from '../../game/mapView';
import { useGameNow } from '../../game/serverTime';
import { canCancel } from '../../game/troops';
import { useI18n } from '../../i18n';
import { CancelConfirm } from '../rally/MovementCards';
import { rallyPath } from '../rally/rallyPaths';
import styles from './MapPage.module.css';
import { MarkerIcon } from './MarkerIcon';

const PERCENT = 100;

interface LinePanelProps {
  line: MapMovementLine;
  onClose: () => void;
  /** The part of the map the panel covers, whenever it appears or changes its size (not when docked). */
  onCover: (cover: Cover) => void;
  /** Glide to the village at the other end of the way and pick it. */
  onShow: (village: VillageRef) => void;
  /** Desktop: a card in the map's side column instead of floating over the map. */
  docked?: boolean;
}

/**
 * A picked movement line (v1.3), in the same place as a picked village: across the bottom of the map
 * on phones, a card on tablets, in the side column on the desktop. When the troops arrive, the way
 * left, the troops where the player may see them, "Cancel" while the window is open (after the same
 * question as at the rally point), the rally point that lists the movement and the other end of the
 * way. Not modal, like the village panel.
 */
export function LinePanel({ line, onClose, onCover, onShow, docked = false }: LinePanelProps) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const titleId = useId();
  const ref = useRef<HTMLElement>(null);
  const [confirming, setConfirming] = useState(false);
  // the error itself, not its text: the message is translated while rendering (language switch)
  const [error, setError] = useState<Error | null>(null);
  const hostile = lineHostile(line);
  // own attacks and supports on their way out; troops coming home and other players' movements come in
  const outgoing = line.own && line.kind !== 'RETURN';
  // the player's village at this line's end: its rally point lists the movement
  const home = outgoing ? line.from : line.to;
  const other = outgoing ? line.to : line.from;
  const troops = useTroops(home.id).data;
  const sent = outgoing ? troops?.outgoing.find((entry) => entry.id === line.id) : undefined;
  const coming = outgoing ? undefined : troops?.incoming.find((entry) => entry.id === line.id);
  const units = sent?.units ?? coming?.units ?? null;
  const cancel = useCancelMovement(home.id);
  const cancelUntil = sent !== undefined && canCancel(sent, now) ? sent.cancellableUntil : null;
  const progress = lineProgress(line, now);
  const fieldsLeft = lineFieldsLeft(line, now);

  const reportCover = useEffectEvent((panel: HTMLElement) =>
    onCover({ top: panel.offsetTop, right: panel.offsetLeft + panel.offsetWidth }),
  );
  useEffect(() => {
    const panel = ref.current;
    if (panel === null || docked) {
      return; // in the side column it covers nothing
    }
    const observer = new ResizeObserver(() => reportCover(panel));
    observer.observe(panel);
    return () => observer.disconnect();
  }, [line.id, docked]);

  const name = (village: VillageRef) =>
    `${village.name ?? t.map.barbarianVillage} ${formatCoords(village.x, village.y)}`;
  let title: string;
  if (!line.own) {
    title = hostile ? t.map.lines.attackFrom(name(line.from)) : t.map.lines.supportFrom(name(line.from));
  } else if (line.kind === 'RETURN') {
    title = t.map.lines.returnTo(name(line.to));
  } else if (line.kind === 'SUPPORT') {
    title = t.map.lines.supportFor(name(line.to));
  } else {
    title = line.nobles ? t.map.lines.nobleAttackOn(name(line.to)) : t.map.lines.attackOn(name(line.to));
  }
  // another player's attack hides its troops; '…' while the rally point's list loads
  let unitsShown: ReactNode = t.map.lines.hidden;
  if (units !== null) {
    unitsShown = <UnitSummary units={units} />;
  } else if (troops === undefined && !hostile) {
    unitsShown = '…';
  }
  let showLabel = t.map.lines.showStart;
  if (hostile) {
    showLabel = t.map.lines.showAttacker;
  } else if (outgoing) {
    showLabel = t.map.lines.showTarget;
  }

  function cancelMovement(id: number) {
    setError(null);
    cancel.mutate(id, {
      onSuccess: () => {
        setConfirming(false);
        onClose();
      },
      onError: (failure) => {
        setConfirming(false);
        setError(failure);
      },
    });
  }

  return (
    <section
      ref={ref}
      className={docked ? `${styles.panel} ${styles.panelDocked}` : styles.panel}
      aria-labelledby={titleId}
      // Escape closes the panel wherever the focus is in it (the map's own Escape needs the canvas focused)
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          onClose();
        }
      }}
    >
      <header className={styles.panelHead}>
        <MarkerIcon kind={lineKind(line)} />
        <h2 id={titleId} className={styles.panelTitle}>
          {title}
        </h2>
        <IconButton icon="close" label={t.common.close} onClick={onClose} />
      </header>
      <dl className={styles.facts}>
        <div>
          <dt>{t.map.lines.arrives}</dt>
          <dd>
            <Countdown until={line.arriveAt} className={styles.lineCountdown} />
            <span className={styles.tag}>{formatWhenExact(Date.parse(line.arriveAt), now, language)}</span>
          </dd>
        </div>
        <div>
          <dt>{t.map.lines.way}</dt>
          <dd>
            {progress === undefined || fieldsLeft === undefined ? (
              t.map.lines.hidden
            ) : (
              <>
                {t.map.lines.fieldsLeft(formatDecimal(fieldsLeft, language))}
                <span className={styles.lineProgress} data-kind={lineKind(line)} aria-hidden="true">
                  <span style={{ width: `${(progress * PERCENT).toFixed(1)}%` }} />
                </span>
              </>
            )}
          </dd>
        </div>
        <div>
          <dt>{t.map.lines.troops}</dt>
          <dd>{unitsShown}</dd>
        </div>
        {!line.own && line.from.playerName !== null && (
          <div>
            <dt>{t.map.owner}</dt>
            <dd>{line.from.playerName}</dd>
          </div>
        )}
      </dl>
      <div className={styles.actions}>
        {sent !== undefined && cancelUntil !== null && (
          <Button type="button" variant="danger" disabled={cancel.isPending} onClick={() => setConfirming(true)}>
            {t.rally.cancel} <Countdown until={cancelUntil} />
          </Button>
        )}
        {error !== null && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
        <div className={styles.leadRow}>
          <Link to={`${rallyPath(home.id)}#${outgoing ? 'outgoing' : 'incoming'}`} className={styles.lead}>
            <Icon name="RALLY_POINT" />
            {t.map.lines.toRally}
          </Link>
          <button type="button" className={styles.second} onClick={() => onShow(other)}>
            <Icon name="locate" />
            {showLabel}
          </button>
        </div>
      </div>
      {/* closes by itself when the cancel window ends meanwhile, as at the rally point */}
      <Sheet open={confirming && cancelUntil !== null} onClose={() => setConfirming(false)} title={t.rally.cancelTitle}>
        {sent !== undefined && (
          <CancelConfirm
            movement={sent}
            pending={cancel.isPending}
            onKeep={() => setConfirming(false)}
            onConfirm={() => cancelMovement(sent.id)}
          />
        )}
      </Sheet>
    </section>
  );
}
