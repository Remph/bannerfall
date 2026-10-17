import { useMemo, useState, type FocusEvent } from 'react';
import { Link } from 'react-router';
import type { BuildOrder } from '../../../api/queries/buildings';
import type { Village } from '../../../api/queries/villages';
import { hasStarted } from '../../../game/buildQueue';
import { buildingTypes, type BuildingType } from '../../../game/gameData';
import { useGameNow } from '../../../game/serverTime';
import { useT } from '../../../i18n';
import { buildingTarget } from '../buildingTarget';
import { BuildingDrawing, Plot, Scaffold } from './drawings';
import { growthOf } from './growth';
import type { SceneLayout } from './layouts';
import { palisade } from './palisade';
import { ConstructionPlate, Plate } from './Plate';
import { levelLabel } from './plates';
import { Tree } from './shapes';
import s from './VillageScene.module.css';

/** How far below its spot a building's plate hangs (the gate's a little less: it stands on the wall). */
const PLATE_DROP = 8;
const GATE_PLATE_DROP = 6;

/**
 * The buildings of a village as one picture (05-frontend, *Village picture*): every building drawn
 * at its spot inside or outside a palisade, growing with its level, each one a link to its screen
 * like the list. Drawn back to front: the back half of the wall, then trees and buildings from the
 * top down, the front half of the wall with the gate, and the plates on top.
 */
export function VillageScene({
  village,
  layout,
  bannersOn,
}: {
  village: Village;
  layout: SceneLayout;
  bannersOn: boolean;
}) {
  const t = useT();
  const [focused, setFocused] = useState<BuildingType | null>(null);
  const levelOf = (type: BuildingType) => village.buildings[type] ?? 0;
  // a world without banners has no use for the Banner Hall: left out unless one stands there (as in the list)
  const shown = buildingTypes.filter((type) => type !== 'BANNER_HALL' || bannersOn || levelOf(type) > 0);
  const running = village.buildQueue[0];
  const wallLevel = levelOf('WALL');
  const stakes = useMemo(() => palisade(layout, wallLevel), [layout, wallLevel]);

  const label = (type: BuildingType) => {
    const level = levelOf(type);
    return `${t.buildings[type]}, ${level === 0 ? t.village.notBuilt : t.village.level(level)}`;
  };
  const focusHandlers = (type: BuildingType) => ({
    onFocus: (event: FocusEvent<Element>) => setFocused(isKeyboardFocus(event.currentTarget) ? type : null),
    onBlur: () => setFocused((current) => (current === type ? null : current)),
  });

  const place = (type: BuildingType) => {
    const level = levelOf(type);
    const { x, y } = layout.spots[type];
    return (
      <Link
        key={type}
        viewTransition
        to={buildingTarget(village.id, type, level)}
        className={s.place}
        aria-label={label(type)}
        data-building={type}
        {...focusHandlers(type)}
      >
        <g transform={`translate(${x} ${y}) scale(${layout.scale})`}>
          <g className={s.lift}>
            {/* an invisible tap area, so a tap between the parts of a drawing still hits it */}
            <rect className={s.hit} x={-40} y={-66} width={80} height={72} />
            {level === 0 ? <Plot /> : <BuildingDrawing type={type} growth={growthOf(type, level)} />}
            {running?.building === type && <ScaffoldWhileRunning order={running} type={type} onPlot={level === 0} />}
          </g>
        </g>
      </Link>
    );
  };

  const depthSorted = [
    ...layout.trees.map(([x, y, r]) => ({
      depth: y + r * 0.8,
      x,
      node: <Tree key={`tree-${x}-${y}`} x={x} y={y} r={layout.scale === 1 ? r : r * 0.95} />,
    })),
    ...shown
      .filter((type) => type !== 'WALL')
      .map((type) => ({ depth: layout.spots[type].y, x: layout.spots[type].x, node: place(type) })),
    // top down, and left to right on a row: also the tab order
  ].sort((a, b) => a.depth - b.depth || a.x - b.x);

  const { cx, cy, rx, ry } = layout.wall;
  return (
    <svg
      className={s.scene}
      viewBox={`0 0 ${layout.width} ${layout.height}`}
      role="group"
      aria-label={t.village.buildingsTitle}
    >
      <rect className={s.grass} width={layout.width} height={layout.height} />
      <ellipse className={s.yard} cx={cx} cy={cy} rx={rx - 8} ry={ry - 8} />
      {layout.paths.map((d) => (
        <path key={d} className={s.path} d={d} strokeWidth={14 * layout.scale + 2} />
      ))}
      {stakes === undefined ? (
        <ellipse className={s.wallPlot} cx={cx} cy={cy} rx={rx} ry={ry} />
      ) : (
        <>
          <ellipse className={s.wallShade} cx={cx + 3} cy={cy + 4} rx={rx} ry={ry} />
          <path className={s.wood} d={stakes.back.front} />
          <path className={s.woodSide} d={stakes.back.side} />
        </>
      )}
      {depthSorted.map((item) => item.node)}
      {stakes !== undefined && (
        <>
          <path className={s.wood} d={stakes.front.front} />
          <path className={s.woodSide} d={stakes.front.side} />
        </>
      )}
      {shown.includes('WALL') && place('WALL')}
      <g>
        {shown.map((type) => {
          const level = levelOf(type);
          const { x, y } = layout.spots[type];
          const plate = {
            x,
            y: y + (type === 'WALL' ? GATE_PLATE_DROP : PLATE_DROP),
            font: layout.font,
            name: t.buildings[type],
            faded: level === 0,
            to: buildingTarget(village.id, type, level),
            focused: focused === type,
          };
          return running?.building === type ? (
            <ConstructionPlate key={type} {...plate} order={running} level={level} />
          ) : (
            <Plate key={type} {...plate} levelText={levelLabel(level)} />
          );
        })}
      </g>
    </svg>
  );
}

/** Scaffolding on the building whose upgrade is running (from its start; re-renders on its own). */
function ScaffoldWhileRunning({ order, type, onPlot }: { order: BuildOrder; type: BuildingType; onPlot: boolean }) {
  const now = useGameNow();
  return hasStarted(order, now) ? <Scaffold type={type} onPlot={onPlot} /> : null;
}

/** Keyboard focus gets the ring; a tap or click that focuses the link doesn't. */
function isKeyboardFocus(element: Element): boolean {
  try {
    return element.matches(':focus-visible');
  } catch {
    return true;
  }
}
