import { useLayoutEffect, useRef, useState, type RefObject } from 'react';
import { Link, type To } from 'react-router';
import type { BuildOrder } from '../../../api/queries/buildings';
import { hasStarted, progress } from '../../../game/buildQueue';
import { useGameNow } from '../../../game/serverTime';
import {
  estimateLevelWidth,
  estimateTextWidth,
  levelLabel,
  PLATE_HEIGHT,
  PLATE_PAD,
  pillWidth,
  plateWidth,
} from './plates';
import s from './VillageScene.module.css';

/** Plate sizes in font sizes (the plate scales with the layout's font). */
const PILL_INSET = 0.22;
const BAR_GAP = 0.2;
const BAR_HEIGHT = 0.26;

export interface PlateProps {
  /** The middle of the plate's top edge. */
  x: number;
  y: number;
  font: number;
  name: string;
  /** "12", "–" for not built, "12 → 13" while being upgraded. */
  levelText: string;
  faded: boolean;
  to: To;
  focused: boolean;
  /** 0–1 while the building is being upgraded: a thin bar under the plate. */
  done?: number;
}

/**
 * The name plate under a building: name and level in a pill. It is a second link to the building's
 * screen, so tapping the plate works too, but hidden from screen readers and the tab order (the
 * drawing's link has the label and the focus; `focused` draws the focus ring here).
 */
export function Plate({ x, y, font, name, levelText, faded, to, focused, done }: PlateProps) {
  const nameRef = useRef<SVGTextElement>(null);
  const levelRef = useRef<SVGTextElement>(null);
  const nameWidth = useTextWidth(nameRef, name, () => estimateTextWidth(name, font), font);
  const levelWidth = useTextWidth(levelRef, levelText, () => estimateLevelWidth(levelText, font), font);

  const height = font * PLATE_HEIGHT;
  const width = plateWidth(nameWidth, levelWidth, font);
  const left = x - width / 2;
  const pill = pillWidth(levelWidth, font);
  const pillHeight = height - font * PILL_INSET * 2;
  const pillLeft = left + width - pill - font * 0.25;
  const middle = y + height / 2;
  return (
    <Link
      viewTransition
      to={to}
      className={faded ? `${s.plate} ${s.plateFaded}` : s.plate}
      aria-hidden="true"
      tabIndex={-1}
    >
      {focused && (
        <rect
          className={s.focusRing}
          x={left - 3}
          y={y - 3}
          width={width + 6}
          height={height + 6}
          rx={height / 2 + 3}
        />
      )}
      <rect className={s.plateBox} x={left} y={y} width={width} height={height} rx={height / 2} />
      <text ref={nameRef} className={s.plateName} x={left + font * PLATE_PAD} y={middle} fontSize={font}>
        {name}
      </text>
      <rect
        className={s.platePill}
        x={pillLeft}
        y={y + font * PILL_INSET}
        width={pill}
        height={pillHeight}
        rx={pillHeight / 2}
      />
      <text ref={levelRef} className={s.plateLevel} x={pillLeft + pill / 2} y={middle} fontSize={font * 0.88}>
        {levelText}
      </text>
      {done !== undefined && (
        <>
          <rect
            className={s.plateTrack}
            x={left + height / 2}
            y={y + height + font * BAR_GAP}
            width={width - height}
            height={font * BAR_HEIGHT}
            rx={(font * BAR_HEIGHT) / 2}
          />
          <rect
            className={s.plateBar}
            x={left + height / 2}
            y={y + height + font * BAR_GAP}
            width={(width - height) * done}
            height={font * BAR_HEIGHT}
            rx={(font * BAR_HEIGHT) / 2}
          />
        </>
      )}
    </Link>
  );
}

/**
 * The drawn width of a plate's text, so long German names fit: the estimate until the text has been
 * measured (and in tests, which have no layout), measured again once the web font has arrived.
 */
function useTextWidth(
  ref: RefObject<SVGTextElement | null>,
  text: string,
  estimate: () => number,
  font: number,
): number {
  const [width, setWidth] = useState(estimate);
  useLayoutEffect(() => {
    const element = ref.current;
    if (element === null || typeof element.getComputedTextLength !== 'function') {
      return;
    }
    const measure = () => {
      const measured = element.getComputedTextLength();
      if (measured > 0) {
        setWidth(measured);
      }
    };
    measure();
    void document.fonts?.ready.then(measure);
  }, [ref, text, font]);
  return width;
}

/**
 * The plate of the building whose upgrade is running: target level and progress bar while it runs
 * (this part re-renders every second, not the whole picture). Before its start (a server that is
 * behind) it is the plain plate.
 */
export function ConstructionPlate({
  order,
  level,
  ...plate
}: Omit<PlateProps, 'levelText' | 'done'> & { order: BuildOrder; level: number }) {
  const now = useGameNow();
  if (!hasStarted(order, now)) {
    return <Plate {...plate} levelText={levelLabel(level)} />;
  }
  return <Plate {...plate} levelText={`${levelLabel(level)} → ${order.targetLevel}`} done={progress(order, now)} />;
}
