import { describe, expect, it } from 'vitest';
import { driftStep, glideCamera, glidePlan, releaseVelocity, type PointerSample } from './mapMotion';

describe('map motion', () => {
  const from = { x: 10, y: 10, tile: 48 };

  it('glides short jumps quickly and straight, long ones longer with a zoom out', () => {
    expect(glidePlan(from, { x: 12, y: 10, tile: 48 })).toEqual({ ms: 404, lift: 0 });
    const far = glidePlan(from, { x: 70, y: 90, tile: 48 });
    expect(far.ms).toBe(1100);
    expect(far.lift).toBeGreaterThan(0);
  });

  it('starts at the camera and ends exactly on the target', () => {
    const to = { x: 70, y: 90, tile: 32 };
    expect(glideCamera(from, to, 0, 0.35)).toEqual(from);
    expect(glideCamera(from, to, 1, 0.35)).toBe(to);
    expect(glideCamera(from, to, 2, 0.35)).toBe(to);
  });

  it('zooms out half way through a long glide', () => {
    const to = { x: 70, y: 90, tile: 48 };
    const middle = glideCamera(from, to, 0.5, 0.35);
    expect(middle.x).toBeCloseTo(40);
    expect(middle.y).toBeCloseTo(50);
    expect(middle.tile).toBeCloseTo(48 * 0.65);
  });

  it('measures the speed of the end of a drag', () => {
    const samples: PointerSample[] = [
      { t: 0, x: 0, y: 0 },
      { t: 100, x: 10, y: 0 },
      { t: 150, x: 30, y: 0 },
      { t: 200, x: 60, y: 20 },
    ];
    // over the last 100 ms: from (10|0) to (60|20)
    expect(releaseVelocity(samples, 210)).toEqual({ x: 0.5, y: 0.2 });
  });

  it("doesn't drift after the pointer stood still or moved slowly", () => {
    const samples: PointerSample[] = [
      { t: 0, x: 0, y: 0 },
      { t: 100, x: 60, y: 0 },
    ];
    expect(releaseVelocity(samples, 160)).toBeNull(); // still for 60 ms
    expect(
      releaseVelocity(
        [
          { t: 0, x: 0, y: 0 },
          { t: 100, x: 5, y: 0 },
        ],
        110,
      ),
    ).toBeNull();
    expect(releaseVelocity([{ t: 0, x: 0, y: 0 }], 10)).toBeNull();
    expect(releaseVelocity([], 10)).toBeNull();
  });

  it('drifts on, slows down and stops', () => {
    let velocity = { x: 1, y: 0 };
    let travelled = 0;
    let frames = 0;
    for (let step = driftStep(velocity, 16); step !== null; step = driftStep(velocity, 16)) {
      travelled += step.dx;
      velocity = step.velocity;
      frames += 1;
    }
    expect(travelled).toBeGreaterThan(100);
    expect(travelled).toBeLessThan(300);
    expect(frames).toBeLessThan(60 * 2); // about a second
  });

  it("doesn't jump after a late frame", () => {
    expect(driftStep({ x: 1, y: 0 }, 500)?.dx).toBe(32);
  });
});
