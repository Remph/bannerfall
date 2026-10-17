import { describe, expect, it } from 'vitest';
import { de } from '../../../i18n/de';
import { en } from '../../../i18n/en';
import { buildingTypes, type BuildingType } from '../../../game/gameData';
import { maxLevels } from './growth';
import { COMPACT, outsideWall, wallDistance, WIDE, type SceneLayout } from './layouts';
import { estimateLevelWidth, estimateTextWidth, PLATE_HEIGHT, plateWidth } from './plates';

const layouts: [string, SceneLayout][] = [
  ['wide', WIDE],
  ['compact', COMPACT],
];

/** A plate's box as the scene draws it (with the estimated, generous widths and the widest level). */
function plateBox(layout: SceneLayout, type: BuildingType, name: string) {
  const { x, y } = layout.spots[type];
  const level = String(maxLevels[type]);
  const width = plateWidth(estimateTextWidth(name, layout.font), estimateLevelWidth(level, layout.font), layout.font);
  const top = y + (type === 'WALL' ? 6 : 8);
  return { left: x - width / 2, right: x + width / 2, top, bottom: top + layout.font * PLATE_HEIGHT };
}

describe.each(layouts)('the %s layout', (_, layout) => {
  it('places every building inside the picture', () => {
    for (const type of buildingTypes) {
      const { x, y } = layout.spots[type];
      expect(x, type).toBeGreaterThan(0);
      expect(x, type).toBeLessThan(layout.width);
      expect(y, type).toBeGreaterThan(0);
      expect(y, type).toBeLessThan(layout.height);
    }
  });

  it('puts the resource sites and the hiding place outside the wall, the rest inside, the gate on it', () => {
    for (const type of buildingTypes) {
      const distance = wallDistance(layout, layout.spots[type]);
      if (type === 'WALL') {
        expect(distance, type).toBeGreaterThan(0.9);
        expect(distance, type).toBeLessThan(1.1);
      } else if (outsideWall.has(type)) {
        expect(distance, type).toBeGreaterThan(1);
      } else {
        expect(distance, type).toBeLessThan(0.9);
      }
    }
  });

  it('keeps free spots inside the wall, away from the buildings, for later buildings', () => {
    expect(layout.free.length).toBeGreaterThan(0);
    for (const spot of layout.free) {
      expect(wallDistance(layout, spot)).toBeLessThan(0.9);
      for (const type of buildingTypes) {
        const { x, y } = layout.spots[type];
        expect(Math.hypot(x - spot.x, y - spot.y), type).toBeGreaterThan(60 * layout.scale);
      }
    }
  });

  it.each([
    ['English', en],
    ['German', de],
  ])('has no overlapping plates in %s', (_, t) => {
    const boxes = buildingTypes.map((type) => ({ type, ...plateBox(layout, type, t.buildings[type]) }));
    for (const box of boxes) {
      expect(box.left, box.type).toBeGreaterThanOrEqual(0);
      expect(box.right, box.type).toBeLessThanOrEqual(layout.width);
      expect(box.bottom, box.type).toBeLessThanOrEqual(layout.height);
    }
    for (const [i, a] of boxes.entries()) {
      for (const b of boxes.slice(i + 1)) {
        const apart = a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top;
        expect(apart, `${a.type} and ${b.type}`).toBe(true);
      }
    }
  });
});
