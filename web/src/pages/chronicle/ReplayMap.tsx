import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { Chronicle } from '../../api/queries/chronicle';
import { IconButton } from '../../components/IconButton';
import { legend, replayStates } from '../../game/chronicle';
import { prefersReducedMotion } from '../../game/motion';
import { sideName } from '../../game/victory';
import { useI18n } from '../../i18n';
import { useEffectiveTheme, usePalette } from '../../theme/useEffectiveTheme';
import { readMapColors } from '../map/mapDraw';
import { useDevicePixelRatio } from '../map/useDevicePixelRatio';
import styles from './ReplayMap.module.css';
import { drawReplay, sideColor } from './replayDraw';

/** How long each day stays on screen while the replay plays. */
export const PLAY_STEP_MS = 900;
/** The legend names this many sides (and the player's own besides). */
const LEGEND_MAX = 5;

interface ReplayMapProps {
  chronicle: Chronicle;
  /** The frame shown (its index in `map.frames`). */
  frame: number;
  onFrame: (frame: number) => void;
  /** A village to ring (its index in `map.villages`), or null. */
  highlight?: number | null;
  /** The world-end screen's small version: no legend. */
  compact?: boolean;
}

/**
 * The replay of who owned what (2.1 I1): the whole map on one day, a slider over the days, a step
 * back and forward, and a play button that walks through the days. Touch first, nothing hover-only;
 * with reduced motion there is no play button (the days are stepped by hand).
 */
export function ReplayMap({ chronicle, frame, onFrame, highlight = null, compact = false }: ReplayMapProps) {
  const { t } = useI18n();
  const map = chronicle.map;
  const states = useMemo(() => replayStates(map), [map]);
  const last = Math.max(0, map.frames.length - 1);
  const shown = Math.min(Math.max(0, frame), last);
  const state = states[shown];
  const day = map.frames[shown]?.day ?? 0;
  const [playing, setPlaying] = useState(false);
  const reduced = prefersReducedMotion();
  const theme = useEffectiveTheme();
  const palette = usePalette(); // a trigger for the redraw, like `theme`
  const pixelRatio = useDevicePixelRatio();
  const boxRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [side, setSide] = useState<number | null>(null);

  useEffect(() => {
    const box = boxRef.current;
    if (box === null) {
      return;
    }
    const observer = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width ?? 0;
      if (width > 0) {
        setSide(width);
      }
    });
    observer.observe(box);
    return () => observer.disconnect();
  }, []);

  useLayoutEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d') ?? null;
    if (canvas === null || context === null || side === null || state === undefined) {
      return;
    }
    const pixels = Math.round(side * pixelRatio);
    if (canvas.width !== pixels || canvas.height !== pixels) {
      canvas.width = pixels;
      canvas.height = pixels;
    }
    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
    drawReplay(context, { side, map, state, colors: readMapColors(canvas), theme, highlight });
  }, [side, pixelRatio, map, state, theme, palette, highlight]);

  // playing walks one day further every step and stops on the last day
  const running = playing && shown < last;
  useEffect(() => {
    if (!running) {
      return;
    }
    const timer = window.setTimeout(() => onFrame(shown + 1), PLAY_STEP_MS);
    return () => window.clearTimeout(timer);
  }, [running, shown, onFrame]);

  const lastDay = map.frames[last]?.day ?? 0;
  const dayText = t.chronicle.dayOf(day, lastDay);
  const mine = new Set(map.sides.flatMap((entry, index) => (entry.mine ? [index] : [])));
  // the legend's swatches: the same colour tokens the canvas reads, from the page
  const colors = useMemo(() => readMapColors(document.documentElement), [theme, palette]); // eslint-disable-line react-hooks/exhaustive-deps
  const counts = state === undefined ? [] : legend(state, mine, LEGEND_MAX);

  return (
    <section
      className={compact ? `${styles.replay} ${styles.compact}` : styles.replay}
      aria-label={t.chronicle.replayLabel}
    >
      <div ref={boxRef} className={styles.box}>
        <canvas
          ref={canvasRef}
          className={styles.canvas}
          role="img"
          aria-label={`${t.chronicle.replayLabel}: ${dayText}`}
        />
      </div>
      <div className={styles.controls}>
        <IconButton
          icon="chevronLeft"
          label={t.chronicle.previous}
          disabled={shown <= 0}
          onClick={() => {
            setPlaying(false);
            onFrame(shown - 1);
          }}
        />
        {!reduced && (
          <button
            type="button"
            className={styles.play}
            aria-label={running ? t.chronicle.pause : t.chronicle.play}
            aria-pressed={running}
            disabled={last === 0}
            onClick={() => {
              if (!running && shown >= last) {
                onFrame(0); // from the start again
              }
              setPlaying(!running);
            }}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.playIcon}>
              {running ? <path d="M6 5h4v14H6zM14 5h4v14h-4z" /> : <path d="M7 4.5v15l12.5-7.5z" />}
            </svg>
          </button>
        )}
        <IconButton
          icon="chevronRight"
          label={t.chronicle.next}
          disabled={shown >= last}
          onClick={() => {
            setPlaying(false);
            onFrame(shown + 1);
          }}
        />
        <strong className={styles.day} aria-live={running ? 'off' : 'polite'}>
          {dayText}
        </strong>
      </div>
      <input
        type="range"
        className={styles.slider}
        min={0}
        max={last}
        step={1}
        value={shown}
        disabled={last === 0}
        aria-label={t.chronicle.slider}
        aria-valuetext={dayText}
        onChange={(event) => {
          setPlaying(false);
          onFrame(Number(event.target.value));
        }}
      />
      {!compact && (
        <ul className={styles.legend}>
          {counts.map(({ side: index, villages }) => {
            const entry = map.sides[index];
            if (entry === undefined) {
              return null;
            }
            return (
              <li key={index} className={entry.mine ? styles.mine : undefined}>
                <span
                  className={styles.swatch}
                  style={{ background: sideColor(entry, colors, theme) }}
                  aria-hidden="true"
                />
                <span className={styles.name}>
                  {sideName(entry, t)}
                  {entry.mine && <span className={styles.yours}> · {t.chronicle.yours}</span>}
                </span>
                <span className={styles.count}>{t.chronicle.villages(villages)}</span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
