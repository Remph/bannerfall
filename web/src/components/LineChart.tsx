import { useLayoutEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { chartDays, nearestIndex, niceMax, valueOn, type ChartPoint } from '../game/charts';
import styles from './LineChart.module.css';

export interface ChartLine {
  /** Stable per entity (a player id), so a line keeps its colour when others come and go. */
  key: string | number;
  label: string;
  points: readonly ChartPoint[];
  /** Index into the chart colours (`--chart-1` …); the line's place in the list if left out. */
  color?: number;
}

interface LineChartProps {
  /** Names the chart for screen readers and heads the table view. */
  label: string;
  lines: readonly ChartLine[];
  formatValue: (value: number) => string;
  formatDay: (day: string) => string;
  /** Header of the day column in the table view. */
  dayLabel: string;
}

const HEIGHT = 168;
/** Room around the plot: the value labels at the left, the first and last day below. */
const PAD_TOP = 10;
const PAD_RIGHT = 10;
const PAD_BOTTOM = 22;
const PAD_LEFT = 48;
const PAD = { top: PAD_TOP, right: PAD_RIGHT, bottom: PAD_BOTTOM, left: PAD_LEFT };
const GRID_STEPS = 4;
/** Every other grid line carries its value; a single day sits in the middle (half the width). */
const LABEL_EVERY = 2;
/** Gap between a value label and the plot. */
const LABEL_GAP = 6;
/** The day labels sit this far above the bottom edge. */
const DAY_LABEL_LIFT = 4;
/** Radius of the picked day's marker (an 8 px dot). */
const DOT_RADIUS = 4;
const COLORS = 5;
/** Until the box is measured (and in tests, which have no layout). */
const DEFAULT_WIDTH = 320;
const KEY_STEPS: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1 };

/**
 * A small line chart over days (1.5 statistics, 05-frontend §3), hand-drawn SVG: the game has no
 * chart library. One value axis from 0, recessive grid, 2 px lines. Tap, drag or the arrow keys
 * pick a day; the line above the plot reads out every line's value on it (the last day until then),
 * so nothing needs hover. Two or more lines get a legend, and a hidden table carries the numbers
 * for screen readers.
 */
export function LineChart({ label, lines, formatValue, formatDay, dayLabel }: LineChartProps) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(DEFAULT_WIDTH);
  const days = chartDays(lines.map((line) => line.points));
  const [picked, setPicked] = useState<number | null>(null);
  const index = picked === null || picked >= days.length ? days.length - 1 : picked;

  useLayoutEffect(() => {
    const element = box.current;
    if (element === null) {
      return;
    }
    const measure = () => {
      if (element.clientWidth > 0) {
        setWidth(element.clientWidth);
      }
    };
    measure();
    if (typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const plotWidth = Math.max(1, width - PAD.left - PAD.right);
  const plotHeight = HEIGHT - PAD.top - PAD.bottom;
  const top = niceMax(Math.max(0, ...lines.flatMap((line) => line.points.map((point) => point.value))), GRID_STEPS);
  const x = (i: number) =>
    PAD.left + (days.length <= 1 ? plotWidth / LABEL_EVERY : (i / (days.length - 1)) * plotWidth);
  const y = (value: number) => PAD.top + plotHeight - (value / top) * plotHeight;
  const colorOf = (line: ChartLine, place: number) => `var(--chart-${((line.color ?? place) % COLORS) + 1})`;

  const pick = (event: PointerEvent<SVGSVGElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width > 0) {
      setPicked(nearestIndex((event.clientX - rect.left - PAD.left) / plotWidth, days.length));
    }
  };
  const step = (event: KeyboardEvent<SVGSVGElement>) => {
    const by = KEY_STEPS[event.key];
    if (by !== undefined && days.length > 0) {
      event.preventDefault();
      setPicked(Math.min(days.length - 1, Math.max(0, index + by)));
    }
  };

  const day = days[index];
  return (
    <figure className={styles.chart}>
      {day !== undefined && (
        <p className={styles.readout} aria-live="polite">
          <span className={styles.readoutDay}>{formatDay(day)}</span>
          {lines.map((line, place) => {
            const value = valueOn(line.points, day);
            return (
              <span key={line.key} className={styles.readoutValue}>
                {lines.length > 1 && (
                  <span className={styles.swatch} style={{ background: colorOf(line, place) }} aria-hidden="true" />
                )}
                {lines.length > 1 && <span className={styles.readoutName}>{line.label}</span>}
                {value === undefined ? '–' : formatValue(value)}
              </span>
            );
          })}
        </p>
      )}
      <div ref={box} className={styles.plot}>
        <svg
          width={width}
          height={HEIGHT}
          viewBox={`0 0 ${width} ${HEIGHT}`}
          role="img"
          aria-label={label}
          tabIndex={0}
          className={styles.svg}
          onPointerDown={pick}
          onPointerMove={(event) => {
            if (event.buttons > 0 || event.pointerType === 'mouse') {
              pick(event);
            }
          }}
          onPointerLeave={(event) => {
            if (event.pointerType === 'mouse') {
              setPicked(null);
            }
          }}
          onKeyDown={step}
        >
          {Array.from({ length: GRID_STEPS + 1 }, (_, i) => {
            const value = (top / GRID_STEPS) * i;
            return (
              <g key={i}>
                <line
                  x1={PAD.left}
                  x2={PAD.left + plotWidth}
                  y1={y(value)}
                  y2={y(value)}
                  className={i === 0 ? styles.baseline : styles.grid}
                />
                {i % LABEL_EVERY === 0 && (
                  <text x={PAD.left - LABEL_GAP} y={y(value)} className={styles.axisValue}>
                    {formatValue(value)}
                  </text>
                )}
              </g>
            );
          })}
          {days.length > 0 && (
            <text
              x={x(0)}
              y={HEIGHT - DAY_LABEL_LIFT}
              className={days.length > 1 ? styles.axisDayStart : styles.axisDayMiddle}
            >
              {formatDay(days[0] ?? '')}
            </text>
          )}
          {days.length > 1 && (
            <text x={x(days.length - 1)} y={HEIGHT - DAY_LABEL_LIFT} className={styles.axisDayEnd}>
              {formatDay(days[days.length - 1] ?? '')}
            </text>
          )}
          {day !== undefined && days.length > 1 && (
            <line x1={x(index)} x2={x(index)} y1={PAD.top} y2={PAD.top + plotHeight} className={styles.crosshair} />
          )}
          {lines.map((line, place) => {
            const color = colorOf(line, place);
            const points = line.points
              .map((point) => ({ i: days.indexOf(point.day), value: point.value }))
              .filter((point) => point.i >= 0);
            const value = day === undefined ? undefined : valueOn(line.points, day);
            return (
              <g key={line.key} style={{ color }}>
                {points.length > 1 && (
                  <polyline
                    points={points.map((point) => `${x(point.i)},${y(point.value)}`).join(' ')}
                    className={styles.line}
                  />
                )}
                {value !== undefined && <circle cx={x(index)} cy={y(value)} r={DOT_RADIUS} className={styles.dot} />}
              </g>
            );
          })}
        </svg>
      </div>
      {lines.length > 1 && (
        <ul className={styles.legend}>
          {lines.map((line, place) => (
            <li key={line.key} className={styles.legendItem}>
              <span className={styles.swatch} style={{ background: colorOf(line, place) }} aria-hidden="true" />
              {line.label}
            </li>
          ))}
        </ul>
      )}
      {/* in a div: a table ignores the 1 px width and would widen a phone page */}
      <div className="sr-only">
        <table>
          <caption>{label}</caption>
          <thead>
            <tr>
              <th scope="col">{dayLabel}</th>
              {lines.map((line) => (
                <th key={line.key} scope="col">
                  {line.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {days.map((d) => (
              <tr key={d}>
                <th scope="row">{formatDay(d)}</th>
                {lines.map((line) => {
                  const value = valueOn(line.points, d);
                  return <td key={line.key}>{value === undefined ? '–' : formatValue(value)}</td>;
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </figure>
  );
}
