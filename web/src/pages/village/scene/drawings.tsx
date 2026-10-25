/*
 * The sixteen buildings of the village picture. Each has three looks (up to a third of its maximum
 * level, up to two thirds, above) and small extras that come with the steps in between (a flag, a
 * fence, a chimney …), so something changes about every fifth of the maximum (growth.ts).
 */
import type { ReactNode } from 'react';
import type { BuildingType } from '../../../game/gameData';
import type { Growth } from './growth';
import {
  Arch,
  Barrel,
  Box,
  Bush,
  Crate,
  Flag,
  GableHouse,
  LogoBanner,
  Poly,
  RoofLong,
  Shadow,
  Spire,
  Windows,
} from './shapes';
import s from './VillageScene.module.css';

type Drawing = (growth: Growth) => ReactNode;

function Headquarters({ look, step }: Growth) {
  const hall = look === 0 ? { x0: -40, w: 80, h: 30, rise: 18 } : { x0: -50, w: 100, h: 36, rise: 22 };
  const windows = look === 0 ? [-32, -18, 18, 32] : [-42, -22, 22, 42];
  const tower = look === 0 ? { x0: -12, w: 24, h: 58, rise: 26 } : { x0: -14, w: 28, h: 76, rise: 30 };
  const top = -tower.h - tower.rise;
  return (
    <>
      {look === 2 &&
        [-80, 50].map((x0) => (
          <g key={x0}>
            <Shadow x0={x0} w={30} />
            <Box x0={x0} w={30} h={24} front={s.plaster} side={s.plasterSide} />
            <RoofLong x0={x0} w={30} h={24} rise={12} />
            <Windows xs={[x0 + 9, x0 + 21]} y={-16} />
            {step >= 5 && <Flag x={x0 + 15} y={-36} className={s.gold} size={0.7} pole={12} />}
          </g>
        ))}
      <Shadow x0={hall.x0} w={hall.w} />
      <Box x0={hall.x0} w={hall.w} h={hall.h} front={s.plaster} side={s.plasterSide} />
      {look > 0 && <path className={s.timber} d={`M${hall.x0} -24 H ${hall.x0 + hall.w} M-30 -36 V 0 M30 -36 V 0`} />}
      <Windows xs={windows} y={-hall.h + 16} />
      <RoofLong x0={hall.x0} w={hall.w} h={hall.h} rise={hall.rise} />
      <Box x0={hall.x0 + hall.w - 22} w={7} h={14} y={-hall.h - 4} front={s.stone} side={s.stoneSide} />
      <Smoke x={hall.x0 + hall.w - 18} y={-hall.h - 18} size={0.7} />
      <Box x0={tower.x0} w={tower.w} h={tower.h} front={s.stone} side={s.stoneSide} />
      <Arch x={0} w={look === 0 ? 12 : 14} h={20} />
      <rect className={s.dark} x={-4} y={-tower.h + 16} width={8} height={11} rx={4} />
      {step >= 3 && <circle className={s.gold} cx={0} cy={-tower.h + 36} r={4} />}
      <Spire x0={tower.x0} w={tower.w} y={-tower.h} rise={tower.rise} />
      {step >= 1 && <Flag x={0} y={top} className={s.green} />}
    </>
  );
}

function Barracks({ look, step }: Growth) {
  const b = look === 0 ? { x0: -38, w: 76, h: 26, rise: 16 } : { x0: -54, w: 108, h: 30, rise: 20 };
  const windows = look === 0 ? [-28, -15, 15, 28] : [-42, -26, 26, 42];
  const right = b.x0 + b.w;
  return (
    <>
      {look === 2 && (
        <>
          <Shadow x0={-80} w={24} />
          <Box x0={-80} w={26} h={20} front={s.wood} side={s.woodSide} />
          <RoofLong x0={-80} w={26} h={20} rise={10} tone="thatch" over={3} />
        </>
      )}
      <Shadow x0={b.x0} w={b.w} />
      <Box x0={b.x0} w={b.w} h={b.h} front={s.plaster} side={s.plasterSide} />
      <Windows xs={windows} y={-b.h + 9} />
      <rect className={s.dark} x={-8} y={-18} width={16} height={18} />
      {step >= 5 && (
        <>
          <circle className={s.gold} cx={-14} cy={-b.h + 8} r={3.5} />
          <circle className={s.gold} cx={14} cy={-b.h + 8} r={3.5} />
        </>
      )}
      <RoofLong x0={b.x0} w={b.w} h={b.h} rise={b.rise} />
      {step >= 1 && (
        <>
          <path
            className={s.woodLine}
            d={`M${right + 8} 0 V -26 M${right + 14} 0 V -30 M${right + 20} 0 V -26 M${right + 4} -14 H ${right + 24}`}
          />
          <Poly
            className={s.stoneSide}
            pts={[
              [right + 6, -26],
              [right + 8, -32],
              [right + 10, -26],
            ]}
          />
          <Poly
            className={s.stoneSide}
            pts={[
              [right + 12, -30],
              [right + 14, -36],
              [right + 16, -30],
            ]}
          />
        </>
      )}
      {step >= 3 && <Flag x={b.x0} y={-b.h} className={s.green} size={0.8} pole={22} />}
    </>
  );
}

function Stable({ look, step }: Growth) {
  const b = look === 0 ? { x0: -26, w: 52, h: 28, rise: 20 } : { x0: -34, w: 68, h: 34, rise: 26 };
  const door = look === 0 ? 18 : 24;
  const right = b.x0 + b.w;
  return (
    <>
      {look === 2 && (
        <>
          <Shadow x0={-60} w={26} />
          <Box x0={-60} w={26} h={22} front={s.barn} side={s.barnSide} />
          <RoofLong x0={-60} w={26} h={22} rise={9} tone="tileDark" over={3} />
        </>
      )}
      <Shadow x0={b.x0} w={b.w} />
      <GableHouse x0={b.x0} w={b.w} h={b.h} rise={b.rise} wall={s.barn} wallSide={s.barnSide} tone="tileDark" />
      <rect className={s.dark} x={-door / 2} y={-door - 2} width={door} height={door + 2} />
      <path
        className={s.woodLine}
        d={`M${-door / 2} ${-door - 2} L${door / 2} 0 M${door / 2} ${-door - 2} L${-door / 2} 0`}
      />
      <rect className={s.dark} x={-6} y={-b.h - 14} width={12} height={9} />
      {step >= 1 && (
        <path
          className={s.woodLine}
          d={`M${right + 6} 0 V -16 M${right + 18} 0 V -16 M${right + 30} 0 V -16 M${right + 6} -12 H ${right + 32} M${right + 6} -5 H ${right + 32}`}
        />
      )}
      {step >= 3 && <ellipse className={s.hay} cx={right + 18} cy={-24} rx={9} ry={6} />}
      {step >= 5 && (
        <>
          <path className={s.line} d={`M0 ${-b.h - b.rise} v -12`} />
          <Poly
            className={s.dark}
            pts={[
              [-7, -b.h - b.rise - 10],
              [7, -b.h - b.rise - 10],
              [3, -b.h - b.rise - 13],
            ]}
          />
        </>
      )}
    </>
  );
}

function Workshop({ look, step }: Growth) {
  const b = look === 0 ? { x0: -30, w: 60, h: 24, rise: 12 } : { x0: -42, w: 84, h: 30, rise: 14 };
  return (
    <>
      <Shadow x0={b.x0} w={b.w} />
      <Box x0={b.x0} w={b.w} h={b.h} front={s.woodDark} side={s.woodSide} />
      <rect className={s.dark} x={b.x0 + 6} y={-b.h + 4} width={b.w - 12} height={b.h - 4} opacity={0.55} />
      <RoofLong x0={b.x0} w={b.w} h={b.h} rise={b.rise} tone="thatch" />
      <circle className={s.wood} cx={b.x0 + 24} cy={-11} r={10} />
      <path className={s.woodLine} d={`M${b.x0 + 14} -11 H ${b.x0 + 34} M${b.x0 + 24} -21 V -1`} />
      {look > 0 && (
        <>
          <path className={s.arm} d="M6 -3 H 32 M12 -3 L 30 -26" />
          <circle className={s.stone} cx={31} cy={-28} r={4} />
        </>
      )}
      {look === 2 && (
        <>
          <rect className={s.wood} x={b.x0 + b.w + 4} y={-12} width={34} height={8} rx={4} />
          <circle className={s.woodTop} cx={b.x0 + b.w + 38} cy={-8} r={4} />
        </>
      )}
      {step >= 2 && <circle className={s.woodTop} cx={b.x0 - 6} cy={-9} r={8} opacity={0.9} />}
      {step >= 4 && <Flag x={b.x0 + b.w} y={-b.h} className={s.gold} size={0.8} pole={18} />}
    </>
  );
}

function Academy() {
  return (
    <>
      <Shadow x0={-38} w={76} />
      <Box x0={-38} w={76} h={32} front={s.plaster} side={s.plasterSide} />
      <path className={s.columns} d="M-30 -30 V 0 M-16 -30 V 0 M16 -30 V 0 M30 -30 V 0" />
      <Box x0={-42} w={84} h={6} y={-32} front={s.stone} side={s.stoneSide} />
      <Arch x={0} w={12} h={16} />
      <path className={s.slate} d="M-24 -38 A 24 24 0 0 1 24 -38 Z" />
      <path className={s.slateSide} d="M6 -38 A 24 24 0 0 0 2 -61.5 A 24 24 0 0 1 24 -38 Z" />
      <path className={s.dome} d="M-18 -42 A 18 18 0 0 1 -6 -56" />
      <path className={s.line} d="M0 -62 V -72" />
      <circle className={s.gold} cx={0} cy={-73} r={3} />
    </>
  );
}

/** Smoke above a chimney whose top is at x, y: three puffs that swell and fade (still with reduced motion). */
function Smoke({ x, y, size = 1 }: { x: number; y: number; size?: number }) {
  return (
    <g className={s.smoke}>
      <circle className={s.puff1} cx={x} cy={y - 6 * size} r={5 * size} />
      <circle className={s.puff2} cx={x + 4 * size} cy={y - 16 * size} r={6 * size} />
      <circle className={s.puff3} cx={x - 1 * size} cy={y - 28 * size} r={7 * size} />
    </g>
  );
}

function Smithy({ look, step }: Growth) {
  const b = look === 0 ? { x0: -26, w: 44, h: 24, rise: 14 } : { x0: -34, w: 60, h: 28, rise: 16 };
  const chimney = b.x0 + b.w - 18;
  const chimneyTop = -b.h - (look === 0 ? 18 : 26);
  const right = b.x0 + b.w;
  return (
    <>
      <Shadow x0={b.x0} w={b.w} />
      <Box x0={b.x0} w={b.w} h={b.h} front={s.stone} side={s.stoneSide} />
      <RoofLong x0={b.x0} w={b.w} h={b.h} rise={b.rise} />
      <Box x0={chimney} w={10} h={-chimneyTop - b.h} y={-b.h} front={s.stone} side={s.stoneSide} />
      <Smoke x={chimney + 5} y={chimneyTop} />
      <rect className={s.dark} x={b.x0 + 8} y={-20} width={22} height={20} />
      <rect className={s.glow} x={b.x0 + 12} y={-12} width={14} height={8} rx={3} />
      {look === 2 && (
        <>
          <rect className={s.woodDark} x={b.x0 - 22} y={-8} width={18} height={8} />
          <rect className={s.window} x={b.x0 - 20} y={-7} width={14} height={3} />
        </>
      )}
      {step >= 1 && (
        <path
          className={s.ore}
          d={`M${right + 4} -14 H ${right + 22} L ${right + 18} -9 H ${right + 16} V -3 H ${right + 20} V 0 H ${right + 6} V -3 H ${right + 10} V -9 H ${right + 7} Z`}
        />
      )}
      {step >= 3 && <Barrel x={right + 24} w={9} h={12} />}
      {step >= 5 && <Flag x={b.x0} y={-b.h} className={s.green} size={0.7} pole={14} />}
    </>
  );
}

function RallyPoint() {
  return (
    <>
      <ellipse className={s.pavingEdge} cx={0} cy={-6} rx={46} ry={15} />
      <ellipse className={s.paving} cx={0} cy={-7} rx={42} ry={12} />
      <path className={s.woodLine} d="M-32 -2 V -22 M-26 -2 V -26 M24 -2 V -22 M30 -2 V -26" />
      <path className={s.pole} d="M0 -6 V -76" />
      <circle className={s.gold} cx={0} cy={-77} r={2.6} />
      <LogoBanner x={1} y={-72} size={46} />
    </>
  );
}

const AWNINGS = [s.red, s.gold, s.green] as const;

function Stall({ x, awning }: { x: number; awning: string | undefined }) {
  return (
    <>
      <path className={s.woodLine} d={`M${x - 14} 0 V -26 M${x + 14} 0 V -26`} />
      <Box x0={x - 15} w={30} h={12} front={s.wood} side={s.woodSide} />
      <circle className={s.ore} cx={x - 6} cy={-14} r={3} />
      <circle className={s.gold} cx={x + 4} cy={-14} r={3} />
      <Poly
        className={awning}
        pts={[
          [x - 18, -24],
          [x - 14, -34],
          [x + 14, -34],
          [x + 18, -24],
        ]}
      />
      <path className={s.stripe} d={`M${x - 8} -34 L${x - 10} -24 M${x} -34 V -24 M${x + 8} -34 L${x + 10} -24`} />
    </>
  );
}

function Market({ look, step }: Growth) {
  const xs = [[0], [-18, 18], [-36, 0, 36]][look] ?? [0];
  const half = 18 * look + 18;
  return (
    <>
      <Shadow x0={-half} w={half * 2} />
      {step >= 4 && <Flag x={-half + 6} y={-26} className={s.gold} size={0.8} pole={24} />}
      {xs.map((x, index) => (
        <Stall key={x} x={x} awning={AWNINGS[(index + 3 - xs.length) % 3]} />
      ))}
      {step >= 2 && (
        <>
          <Crate x={-half - 24} />
          <Crate x={half + 6} w={9} h={9} />
        </>
      )}
    </>
  );
}

function Woodcutter({ look, step }: Growth) {
  const hut = look === 2 ? { x0: -38, w: 38, h: 26, rise: 18 } : { x0: -34, w: 32, h: 22, rise: 16 };
  const logs: readonly (readonly [number, number])[] = [
    [11, -9],
    [20, -9],
    [29, -9],
    ...(look > 0
      ? ([
          [38, -9],
          [16, -18],
          [25, -18],
          [34, -18],
        ] as const)
      : []),
    ...(step >= 4 ? ([[30, -27]] as const) : []),
  ];
  return (
    <>
      <Shadow x0={hut.x0} w={hut.w + 40} />
      <GableHouse x0={hut.x0} w={hut.w} h={hut.h} rise={hut.rise} wall={s.wood} wallSide={s.woodSide} tone="tileDark" />
      <rect className={s.dark} x={hut.x0 + 10} y={-14} width={9} height={14} />
      <rect
        className={s.woodSide}
        x={8}
        y={look > 0 ? -22 : -13}
        width={look > 0 ? 34 : 25}
        height={look > 0 ? 18 : 9}
      />
      {logs.map(([x, y]) => (
        <g key={`${x}${y}`}>
          <circle className={s.woodTop} cx={x} cy={y} r={4.5} />
          <circle className={s.woodSide} cx={x} cy={y} r={1.8} />
        </g>
      ))}
      {step >= 1 && (
        <>
          <Box x0={hut.x0 - 18} w={12} h={8} front={s.wood} side={s.woodSide} />
          <path className={s.woodLine} d={`M${hut.x0 - 14} -8 L ${hut.x0 - 6} -20`} />
        </>
      )}
      {look === 2 && <path className={s.woodLine} d="M46 0 L 54 -14 M62 0 L 54 -14 M46 -10 H 62" />}
    </>
  );
}

function ClayPit({ look, step }: Growth) {
  const k = look === 0 ? 0.7 : 1;
  const stacks = look === 0 ? 0 : look === 1 ? (step >= 3 ? 2 : 1) : 3;
  const bricks: readonly (readonly [number, number, number])[] = [
    [36, 0, 22],
    [38, -6, 18],
    [41, -12, 12],
  ];
  return (
    <>
      <ellipse className={s.clay} cx={-6} cy={-8} rx={44 * k} ry={16 * k} />
      <ellipse className={s.clayDeep} cx={-6} cy={-6} rx={30 * k} ry={10 * k} />
      <ellipse className={s.clayDeeper} cx={-6} cy={-4} rx={15 * k} ry={4.5 * k} />
      {step >= 1 && (
        <path className={s.thinWood} d="M-32 -16 L-24 -2 M-28 -16 L-20 -2 M-31 -13 H -26 M-29 -9 H -23 M-27 -5 H -21" />
      )}
      {bricks.slice(0, stacks).map(([x, y, w]) => (
        <Box key={y} x0={x} w={w} h={6} y={y} front={s.brick} side={s.brickSide} />
      ))}
      {look === 2 && <ellipse className={s.clayDeep} cx={-48} cy={-2} rx={14} ry={5} />}
    </>
  );
}

function IronMine({ look, step }: Growth) {
  const k = look === 0 ? 0.75 : 1;
  const p = (x: number, y: number) => [x * k, y * k] as const;
  return (
    <>
      <Poly className={s.rock} pts={[p(-54, 2), p(-36, -40), p(-10, -58), p(16, -50), p(36, -30), p(54, 2)]} />
      <Poly className={s.rockLight} pts={[p(-36, -40), p(-10, -58), p(-6, -36), p(-22, -24)]} />
      <Poly className={s.rockSide} pts={[p(16, -50), p(36, -30), p(54, 2), p(22, 2), p(18, -26)]} />
      <Arch x={-2} w={22 * k} h={22 * k} />
      <path className={s.timberWide} d={`M${-14 * k} 0 V ${-20 * k} H ${10 * k} V 0`} />
      {look === 2 && <path className={s.timber} d="M-18 -22 L -2 -40 L 14 -22 M-2 -40 V -46" />}
      {step >= 2 && <path className={s.woodLine} d="M10 3 H 56" />}
      {step >= 3 && (
        <>
          <Box x0={26} w={22} h={12} y={-3} front={s.woodDark} side={s.woodSide} />
          <path className={s.ore} d="M27 -15 Q 37 -24 47 -15 Z" />
          <circle className={s.dark} cx={31} cy={-2} r={3} />
          <circle className={s.dark} cx={43} cy={-2} r={3} />
        </>
      )}
      {step >= 5 && <path className={s.ore} d="M-52 2 Q -44 -10 -34 2 Z" />}
    </>
  );
}

function Farm({ look, step }: Growth) {
  return (
    <>
      <Poly
        className={s.field}
        pts={[
          [-72, 2],
          [-42, -26],
          [-8, -26],
          [-28, 2],
        ]}
      />
      <path className={s.furrow} d="M-64 -6 L-33 -6 M-57 -12 L-28 -12 M-50 -19 L-21 -19" />
      {look > 0 && (
        <Poly
          className={s.fieldAlt}
          pts={[
            [-26, -30],
            [-4, -46],
            [22, -46],
            [4, -30],
          ]}
        />
      )}
      {look === 2 && (
        <Poly
          className={s.field}
          pts={[
            [26, -34],
            [40, -48],
            [64, -48],
            [52, -34],
          ]}
        />
      )}
      <Shadow x0={0} w={38} />
      <GableHouse x0={0} w={38} h={24} rise={18} wall={s.plaster} wallSide={s.plasterSide} tone="thatch" />
      <Box x0={27} w={6} h={13} y={-31} front={s.stone} side={s.stoneSide} />
      <Smoke x={30} y={-44} size={0.7} />
      <rect className={s.dark} x={6} y={-14} width={9} height={14} />
      <rect className={s.window} x={24} y={-18} width={8} height={7} />
      {(look === 2 || step >= 3) && (
        <>
          <path className={s.hay} d="M46 0 Q 46 -22 58 -22 Q 70 -22 70 0 Z" />
          <path className={s.hayShade} d="M60 0 Q 68 -12 62 -21 Q 70 -18 70 0 Z" />
        </>
      )}
      {step >= 2 && (
        <path className={s.woodLine} d="M-76 4 H -30 M-76 4 V -6 M-60 4 V -6 M-44 4 V -6 M-30 4 V -6 M-76 -2 H -30" />
      )}
      {step >= 4 && (
        <>
          <path className={s.woodLine} d="M-30 -36 V -58 M-38 -50 H -22" />
          <circle className={s.hay} cx={-30} cy={-60} r={4} />
        </>
      )}
    </>
  );
}

function Warehouse({ look, step }: Growth) {
  const b = look === 0 ? { x0: -22, w: 44, h: 36, rise: 18 } : { x0: -30, w: 60, h: 46, rise: 22 };
  const right = b.x0 + b.w;
  return (
    <>
      {look === 2 && (
        <>
          <Shadow x0={right} w={24} />
          <Box x0={right} w={24} h={26} front={s.woodDark} side={s.woodSide} />
          <RoofLong x0={right} w={24} h={26} rise={10} over={3} />
        </>
      )}
      <Shadow x0={b.x0} w={b.w} />
      <Box x0={b.x0} w={b.w} h={b.h} front={s.wood} side={s.woodSide} />
      <path
        className={s.planks}
        d={`M${b.x0} ${-b.h + 12} H ${right} M${b.x0} ${-b.h + 24} H ${right} M${b.x0} ${-b.h + 36} H ${right}`}
      />
      <rect className={s.dark} x={-10} y={-20} width={20} height={20} />
      <RoofLong x0={b.x0} w={b.w} h={b.h} rise={b.rise} />
      {look > 0 && <rect className={s.dark} x={-7} y={-40} width={14} height={9} />}
      {step >= 1 && <Barrel x={b.x0 - 16} />}
      {step >= 2 && <Crate x={b.x0 - 30} w={12} h={10} />}
      {step >= 3 && look > 0 && (
        <>
          <path className={s.line} d={`M0 ${-b.h - 6} H 18 V ${-b.h + 6}`} />
          <rect className={s.wood} x={13} y={-b.h + 6} width={10} height={9} />
        </>
      )}
      {step >= 4 && <Barrel x={look === 2 ? right + 26 : right + 4} w={11} h={14} />}
    </>
  );
}

function HidingPlace({ look, step }: Growth) {
  return (
    <>
      <Bush x={-26} y={-6} r={12} />
      <Bush x={28} y={-4} r={10} />
      {look === 0 ? (
        <Box x0={-12} w={24} h={6} front={s.wood} side={s.woodSide} />
      ) : (
        <>
          <Shadow x0={-16} w={34} />
          <Box x0={-16} w={34} h={12} front={s.wood} side={s.woodSide} />
          <path className={s.planks} d="M-16 -6 H 18" />
        </>
      )}
      {step >= 2 && <circle className={s.gold} cx={1} cy={look === 0 ? -3 : -6} r={2.5} />}
      {(look === 2 || step >= 4) && <Bush x={-10} y={4} r={7} />}
      {look === 2 && <Bush x={44} y={2} r={7} />}
    </>
  );
}

function BannerHall({ look }: Growth) {
  const b = look === 0 ? { x0: -32, w: 64, h: 32, rise: 22 } : { x0: -40, w: 80, h: 40, rise: 26 };
  const inner = b.x0 + 8;
  return (
    <>
      <Shadow x0={b.x0} w={b.w} />
      <GableHouse x0={b.x0} w={b.w} h={b.h} rise={b.rise} wall={s.stone} wallSide={s.stoneSide} tone="slate" />
      <Arch x={0} w={look === 0 ? 14 : 18} h={look === 0 ? 20 : 26} />
      <Poly
        className={s.green}
        pts={[
          [inner, -b.h + 4],
          [inner + 14, -b.h + 4],
          [inner + 14, -b.h + 28],
          [inner + 7, -b.h + 23],
          [inner, -b.h + 28],
        ]}
      />
      {look > 0 && (
        <Poly
          className={s.gold}
          pts={[
            [18, -36],
            [32, -36],
            [32, -12],
            [25, -17],
            [18, -12],
          ]}
        />
      )}
      {look === 2 && <Flag x={0} y={-b.h - b.rise} className={s.gold} size={0.9} />}
    </>
  );
}

/** The gate in the palisade; the stakes themselves are drawn with the wall (VillageScene). */
function Gate({ look, step }: Growth) {
  const tower = [
    { w: 12, h: 30 },
    { w: 18, h: 44 },
    { w: 20, h: 52 },
  ][look] ?? { w: 18, h: 44 };
  const gap = 22;
  return (
    <>
      {[-gap - tower.w, gap].map((x0) => (
        <g key={x0}>
          <Shadow x0={x0} w={tower.w} />
          <Box x0={x0} w={tower.w} h={tower.h} front={s.wood} side={s.woodSide} />
          {look > 0 && <Spire x0={x0} w={tower.w} y={-tower.h} rise={16} tone="tile" />}
          {step >= 4 && <Flag x={x0 + tower.w / 2} y={-tower.h - 16} className={s.green} size={0.7} pole={10} />}
        </g>
      ))}
      <Box x0={-gap} w={gap * 2} h={6} y={-30} front={s.wood} side={s.woodSide} />
      {step >= 2 && <RoofLong x0={-gap} w={gap * 2} h={36} rise={6} over={2} />}
      <path className={s.dark} d={`M${-gap} 0 V -22 Q 0 -34 ${gap} -22 V 0 Z`} />
    </>
  );
}

const drawings: Record<BuildingType, Drawing> = {
  HEADQUARTERS: Headquarters,
  BARRACKS: Barracks,
  STABLE: Stable,
  WORKSHOP: Workshop,
  BANNER_HALL: BannerHall,
  ACADEMY: Academy,
  SMITHY: Smithy,
  RALLY_POINT: RallyPoint,
  MARKET: Market,
  WOODCUTTER: Woodcutter,
  CLAY_PIT: ClayPit,
  IRON_MINE: IronMine,
  FARM: Farm,
  WAREHOUSE: Warehouse,
  HIDING_PLACE: HidingPlace,
  WALL: Gate,
};

/** Where the scaffolding stands while a building is being upgraded: its left edge, in drawing units. */
const scaffoldAt: Record<BuildingType, number> = {
  HEADQUARTERS: -58,
  BARRACKS: -62,
  STABLE: -42,
  WORKSHOP: -50,
  BANNER_HALL: -48,
  ACADEMY: -46,
  SMITHY: -42,
  RALLY_POINT: -44,
  MARKET: -62,
  WOODCUTTER: -44,
  CLAY_PIT: -52,
  IRON_MINE: -58,
  FARM: -8,
  WAREHOUSE: -38,
  HIDING_PLACE: -24,
  WALL: -48,
};

/** A building's drawing at its growth (the wall's is its gate). */
export function BuildingDrawing({ type, growth }: { type: BuildingType; growth: Growth }) {
  return drawings[type](growth);
}

/** Poles, planks and a hammer that taps (unless motion is reduced), at the building's left side or on its plot. */
export function Scaffold({ type, onPlot = false }: { type: BuildingType; onPlot?: boolean }) {
  // a building going up from level 0 has no walls yet: the scaffold stands on the middle of its plot
  const x = onPlot ? -11 : scaffoldAt[type];
  return (
    <g>
      <path
        className={s.scaffold}
        d={`M${x} 2 V -50 M${x + 22} 2 V -50 M${x - 3} -16 H ${x + 25} M${x - 3} -34 H ${x + 25} M${x} -16 L ${x + 22} -34`}
      />
      <g className={s.hammer} style={{ transformOrigin: `${x + 16}px -36px` }}>
        <path className={s.woodLine} d={`M${x + 16} -36 L ${x + 8} -46`} />
        <rect className={s.ore} x={x + 4} y={-50} width={8} height={5} rx={1} />
      </g>
    </g>
  );
}

/** A building not built yet: a dashed plot with a plus. */
export function Plot() {
  return (
    <>
      <rect className={s.plot} x={-34} y={-30} width={68} height={30} rx={6} />
      <path className={s.plotPlus} d="M0 -22 V -8 M-7 -15 H 7" />
    </>
  );
}
