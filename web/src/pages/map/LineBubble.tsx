import { useLayoutEffect, useRef } from 'react';
import type { MapMovementLine } from '../../api/queries/map';
import { Countdown } from '../../components/Countdown';
import { formatCoords, formatDecimal, formatWhenExact } from '../../game/format';
import { lineDistance, lineKind, lineProgress } from '../../game/mapLines';
import type { Point, Size } from '../../game/mapView';
import { useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import type { VillageRef } from '../../api/queries/troops';
import styles from './MapPage.module.css';

/** The bubble keeps this far from the map's edges (CSS px). */
const EDGE_GAP = 8;

/**
 * The bubble of a tapped (or, on desktop, hovered) movement line (v1.3): what it is and where it
 * goes, the time left and the arrival to the second, and for troops whose position is known the
 * fields still ahead. Placed above the troops (below them where there is no room above), and moved
 * sideways at the left and right edges, so it stays on the map.
 */
export function LineBubble({
  line,
  position,
  mapSize,
  offset,
}: {
  line: MapMovementLine;
  /** The troops (or the middle of a hidden attack's way) on screen. */
  position: Point;
  mapSize: Size;
  /** Half the badge plus a gap: the bubble keeps clear of it. */
  offset: number;
}) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const ref = useRef<HTMLDivElement>(null);
  // placed after measuring, before the browser paints
  useLayoutEffect(() => {
    const bubble = ref.current;
    if (bubble === null) {
      return;
    }
    const { offsetWidth: width, offsetHeight: height } = bubble;
    const left = Math.min(Math.max(EDGE_GAP, position.x - width / 2), mapSize.width - width - EDGE_GAP);
    const above = position.y - offset - height;
    const top = above >= EDGE_GAP ? above : Math.min(position.y + offset, mapSize.height - height - EDGE_GAP);
    bubble.style.left = `${Math.max(EDGE_GAP, left)}px`;
    bubble.style.top = `${Math.max(EDGE_GAP, top)}px`;
  });
  const progress = lineProgress(line, now);
  const kind = lineKind(line);
  const name = (village: VillageRef) =>
    `${village.name ?? t.map.barbarianVillage} ${formatCoords(village.x, village.y)}`;
  let title: string;
  if (!line.own) {
    title = line.kind === 'ATTACK' ? t.map.lines.attackFrom(name(line.from)) : t.map.lines.supportFrom(name(line.from));
  } else if (line.kind === 'RETURN') {
    title = t.map.lines.returnTo(name(line.to));
  } else if (line.kind === 'SUPPORT') {
    title = t.map.lines.supportFor(name(line.to));
  } else {
    title = line.nobles ? t.map.lines.nobleAttackOn(name(line.to)) : t.map.lines.attackOn(name(line.to));
  }
  return (
    <div ref={ref} className={styles.lineBubble}>
      <span>
        <strong>{title}</strong>
        {!line.own && line.from.playerName !== null && <span className={styles.muted}> · {line.from.playerName}</span>}
      </span>
      <span className={styles.lineTime}>
        <Countdown until={line.arriveAt} className={styles.lineCountdown} /> ·{' '}
        {t.rally.arrivesAt(formatWhenExact(Date.parse(line.arriveAt), now, language))}
      </span>
      {progress === undefined ? (
        <span className={styles.muted}>{t.map.lines.positionHidden}</span>
      ) : (
        <>
          <span className={styles.muted}>
            {t.map.lines.fieldsLeft(formatDecimal(lineDistance(line) * (1 - progress), language))}
          </span>
          <span className={styles.lineProgress} data-kind={kind} aria-hidden="true">
            <span style={{ width: `${(progress * 100).toFixed(1)}%` }} />
          </span>
        </>
      )}
    </div>
  );
}
