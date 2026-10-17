/*
 * The building blocks of the village picture, in the drawings' own units (a building's spot is 0, 0:
 * the middle of its front edge; up is negative y). Front view without outlines, lit from the top
 * left: the right part of a wall and of a roof lies in shade, roofs get a light strip on the left.
 */
import s from './VillageScene.module.css';

export type Tone = 'tile' | 'tileDark' | 'thatch' | 'slate';

/** A roof's main, lit and shaded colour. */
const ROOF: Record<Tone, readonly [string | undefined, string | undefined, string | undefined]> = {
  tile: [s.roof, s.roofLight, s.roofSide],
  tileDark: [s.roofSide, s.roof, s.roofSide],
  thatch: [s.thatch, s.thatchLight, s.thatchSide],
  slate: [s.slate, s.slateLight, s.slateSide],
};

type Pt = readonly [number, number];

function points(list: readonly Pt[]): string {
  return list.map(([x, y]) => `${round(x)},${round(y)}`).join(' ');
}

function round(value: number): number {
  return Math.round(value * 10) / 10;
}

export function Poly({
  pts,
  className,
  opacity,
}: {
  pts: readonly Pt[];
  className: string | undefined;
  opacity?: number;
}) {
  return <polygon className={className} points={points(pts)} opacity={opacity} />;
}

/** The soft shadow on the ground, falling to the lower right. */
export function Shadow({ x0, w }: { x0: number; w: number }) {
  const rx = w / 2 + 12;
  return <ellipse className={s.shadow} cx={x0 + w / 2 + 8} cy={3} rx={rx} ry={rx * 0.16} />;
}

/** A wall block with its right quarter in shade, standing at height `y` (0 = the ground). */
export function Box({
  x0,
  w,
  h,
  y = 0,
  front,
  side,
}: {
  x0: number;
  w: number;
  h: number;
  y?: number;
  front: string | undefined;
  side: string | undefined;
}) {
  return (
    <>
      <rect className={front} x={x0} y={y - h} width={w} height={h} />
      <rect className={side} x={x0 + w * 0.72} y={y - h} width={w * 0.28} height={h} opacity={0.55} />
    </>
  );
}

/** A gable roof seen from its long side, on top of a block of height `h`. */
export function RoofLong({
  x0,
  w,
  h,
  rise,
  tone = 'tile',
  over = 5,
}: {
  x0: number;
  w: number;
  h: number;
  rise: number;
  tone?: Tone;
  over?: number;
}) {
  const [main, light, side] = ROOF[tone];
  const x1 = x0 + w;
  const k = rise * 0.55;
  return (
    <>
      <Poly
        className={main}
        pts={[
          [x0 - over, -h],
          [x1 + over, -h],
          [x1 + over - k, -h - rise],
          [x0 - over + k, -h - rise],
        ]}
      />
      <Poly
        className={light}
        pts={[
          [x0 - over, -h],
          [x0 - over + k, -h - rise],
          [x0 - over + k + 12, -h - rise],
          [x0 - over + 14, -h],
        ]}
      />
      <Poly
        className={side}
        pts={[
          [x1 + over - w * 0.3, -h],
          [x1 + over, -h],
          [x1 + over - k, -h - rise],
          [x1 + over - k - w * 0.22, -h - rise],
        ]}
      />
    </>
  );
}

/** A house whose gable faces the front: the walls and the roof together. */
export function GableHouse({
  x0,
  w,
  h,
  rise,
  wall,
  wallSide,
  tone = 'tile',
  over = 4,
}: {
  x0: number;
  w: number;
  h: number;
  rise: number;
  wall: string | undefined;
  wallSide: string | undefined;
  tone?: Tone;
  over?: number;
}) {
  const [main, light, side] = ROOF[tone];
  const x1 = x0 + w;
  const xm = x0 + w / 2;
  return (
    <>
      <Box x0={x0} w={w} h={h} front={wall} side={wallSide} />
      <Poly
        className={main}
        pts={[
          [x0 - over, -h],
          [xm, -h - rise],
          [x1 + over, -h],
        ]}
      />
      <Poly
        className={light}
        pts={[
          [x0 - over, -h],
          [xm, -h - rise],
          [xm - w * 0.18, -h],
        ]}
      />
      <Poly
        className={side}
        pts={[
          [xm, -h - rise],
          [x1 + over, -h],
          [xm + w * 0.2, -h],
        ]}
      />
    </>
  );
}

/** A pointed roof (a spire, a tower roof) on top of a block at height `y`. */
export function Spire({
  x0,
  w,
  y,
  rise,
  tone = 'slate',
}: {
  x0: number;
  w: number;
  y: number;
  rise: number;
  tone?: Tone;
}) {
  const [main, light, side] = ROOF[tone];
  const xm = x0 + w / 2;
  return (
    <>
      <Poly
        className={main}
        pts={[
          [x0 - 3, y],
          [xm, y - rise],
          [x0 + w + 3, y],
        ]}
      />
      <Poly
        className={light}
        pts={[
          [x0 - 3, y],
          [xm, y - rise],
          [x0 + w * 0.32, y],
        ]}
      />
      <Poly
        className={side}
        pts={[
          [xm, y - rise],
          [x0 + w + 3, y],
          [x0 + w * 0.68, y],
        ]}
      />
    </>
  );
}

export function Windows({ xs, y, w = 8, h = 9 }: { xs: readonly number[]; y: number; w?: number; h?: number }) {
  return (
    <>
      {xs.map((x) => (
        <rect key={x} className={s.window} x={x - w / 2} y={y} width={w} height={h} rx={1} />
      ))}
    </>
  );
}

/** A round-topped doorway standing on the ground. */
export function Arch({ x, w, h }: { x: number; w: number; h: number }) {
  return (
    <path
      className={s.dark}
      d={`M${x - w / 2} 0 V ${-h + w / 2} Q ${x} ${-h - w * 0.15} ${x + w / 2} ${-h + w / 2} V 0 Z`}
    />
  );
}

/** A pennant on a pole whose foot is at x, y. Sways (`wave`) unless motion is reduced. */
export function Flag({
  x,
  y,
  className,
  size = 1,
  pole = 16,
}: {
  x: number;
  y: number;
  className: string | undefined;
  size?: number;
  pole?: number;
}) {
  return (
    <>
      <path className={s.line} d={`M${x} ${y} V ${y - pole}`} />
      <g className={s.wave} style={{ transformOrigin: `${x}px ${y - pole}px` }}>
        <Poly
          className={className}
          pts={[
            [x, y - pole],
            [x + 18 * size, y - pole + 4 * size],
            [x, y - pole + 10 * size],
          ]}
        />
      </g>
    </>
  );
}

/** The banner of the game's icon: a red swallowtail cloth with a gold stripe on a gold bar, tipping over. */
export function LogoBanner({ x, y, size, rotate = -14 }: { x: number; y: number; size: number; rotate?: number }) {
  return (
    <g transform={`translate(${x} ${y}) rotate(${rotate}) scale(${round(size / 32)}) translate(-16 -8)`}>
      <g className={s.wave} style={{ transformOrigin: '16px 8px' }}>
        <line className={s.goldStroke} x1={8} y1={8} x2={24} y2={8} strokeWidth={2.4} />
        <path className={s.red} d="M10 9h12v19l-6-4.5-6 4.5z" />
        <path className={s.roofSide} d="M16 9h6v19l-6-4.5z" opacity={0.35} />
        <path className={s.gold} d="M14.6 13.5h2.8v6h-2.8z" />
      </g>
    </g>
  );
}

/** The three leafy lobes of a tree crown (x, y, radius in tree radii), as on the map. */
const LOBES: readonly (readonly [number, number, number])[] = [
  [-0.44, -0.28, 0.6],
  [0.4, -0.2, 0.54],
  [-0.04, -0.66, 0.6],
];

/** A tree whose foot is at x, y: trunk, a crown of three lobes shaded on the lower right, lit spots. */
export function Tree({ x, y, r }: { x: number; y: number; r: number }) {
  return (
    <g>
      <ellipse className={s.treeShade} cx={x + r * 0.25} cy={y + r * 0.85} rx={r * 0.95} ry={r * 0.3} opacity={0.3} />
      <rect className={s.treeShade} x={x - r * 0.1} y={y - r * 0.1} width={r * 0.2} height={r * 0.9} />
      <Lobes x={x} y={y} r={r} lobes={LOBES} />
    </g>
  );
}

const BUSH_LOBES: readonly (readonly [number, number, number])[] = [
  [-0.5, 0.05, 0.62],
  [0.45, 0.1, 0.56],
  [0, -0.32, 0.66],
];

/** A low shrub: the tree's lobes without a trunk. */
export function Bush({ x, y, r }: { x: number; y: number; r: number }) {
  return (
    <g>
      <ellipse className={s.treeShade} cx={x + r * 0.2} cy={y + r * 0.6} rx={r * 1.15} ry={r * 0.3} opacity={0.3} />
      <Lobes x={x} y={y} r={r} lobes={BUSH_LOBES} />
    </g>
  );
}

function Lobes({
  x,
  y,
  r,
  lobes,
}: {
  x: number;
  y: number;
  r: number;
  lobes: readonly (readonly [number, number, number])[];
}) {
  return (
    <>
      {lobes.map(([lx, ly, lr]) => (
        <circle key={`s${lx}`} className={s.treeShade} cx={x + (lx + 0.1) * r} cy={y + (ly + 0.14) * r} r={lr * r} />
      ))}
      {lobes.map(([lx, ly, lr]) => (
        <circle key={`c${lx}`} className={s.tree} cx={x + lx * r} cy={y + ly * r} r={lr * r} />
      ))}
      {lobes.map(([lx, ly, lr]) => (
        <circle
          key={`l${lx}`}
          className={s.treeLight}
          cx={x + (lx - 0.16) * r}
          cy={y + (ly - 0.18) * r}
          r={lr * r * 0.42}
        />
      ))}
    </>
  );
}

/** A small crate or barrel-like block standing at height `y`. */
export function Crate({ x, y = 0, w = 10, h = 10 }: { x: number; y?: number; w?: number; h?: number }) {
  return <Box x0={x} w={w} h={h} y={y} front={s.wood} side={s.woodSide} />;
}

export function Barrel({ x, w = 12, h = 16 }: { x: number; w?: number; h?: number }) {
  return <rect className={s.woodDark} x={x} y={-h} width={w} height={h} rx={4} />;
}
