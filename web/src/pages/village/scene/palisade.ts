import { growthOf } from './growth';
import type { SceneLayout } from './layouts';

/** Stake heights by the wall's look (growth.ts): taller as the wall grows. */
const STAKE_HEIGHTS = [11, 15, 18] as const;
/** About one stake every this many units along the wall. */
const STAKE_SPACING = 8.5;
const STAKE_HALF_WIDTH = 4.2;
/** No stakes this far left and right of the gate. */
const GATE_HALF_WIDTH = 40;

interface StakePaths {
  front: string;
  side: string;
}

/**
 * The palisade as two paths per half (lit fronts, shaded right sides), the back half to draw behind
 * the buildings and the front half in front of them. Undefined while the wall is at level 0: then
 * it is a dashed oval.
 */
export function palisade(layout: SceneLayout, level: number): { back: StakePaths; front: StakePaths } | undefined {
  if (level === 0) {
    return undefined;
  }
  const k = layout.scale;
  const height = STAKE_HEIGHTS[growthOf('WALL', level).look] * k;
  const { cx, cy, rx, ry } = layout.wall;
  const count = Math.floor((2 * Math.PI * Math.sqrt((rx * rx + ry * ry) / 2)) / (STAKE_SPACING * k));
  const halves = { back: { front: '', side: '' }, front: { front: '', side: '' } };
  // from the top round to the bottom, so the stakes of each half lie over the ones behind them
  const order = Array.from({ length: count }, (_, i) => (2 * Math.PI * i) / count).sort(
    (a, b) => Math.sin(a) - Math.sin(b),
  );
  for (const angle of order) {
    const x = cx + rx * Math.cos(angle);
    const y = cy + ry * Math.sin(angle);
    const inFront = y > cy;
    if (inFront && Math.abs(x - cx) < GATE_HALF_WIDTH * k) {
      continue;
    }
    const w = STAKE_HALF_WIDTH * k;
    const half = inFront ? halves.front : halves.back;
    half.front += `M${f(x - w)} ${f(y + 3)}V${f(y - height + 4 * k)}L${f(x)} ${f(y - height)}L${f(x + w)} ${f(y - height + 4 * k)}V${f(y + 3)}Z`;
    half.side += `M${f(x + 0.8 * k)} ${f(y + 3)}V${f(y - height + k)}L${f(x + w)} ${f(y - height + 4 * k)}V${f(y + 3)}Z`;
  }
  return halves;
}

function f(value: number): string {
  return (Math.round(value * 10) / 10).toString();
}
