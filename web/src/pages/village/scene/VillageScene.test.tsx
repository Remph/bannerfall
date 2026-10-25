// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';
import type { Village } from '../../../api/queries/villages';
import { buildingTypes } from '../../../game/gameData';
import { I18nContext } from '../../../i18n';
import { en } from '../../../i18n/en';
import { COMPACT, WIDE } from './layouts';
import { palisade } from './palisade';
import { VillageScene } from './VillageScene';

const MINUTE = 60_000;

function village(buildings: Record<string, number>, buildQueue: Village['buildQueue'] = []): Village {
  return { id: 7, buildings, buildQueue } as unknown as Village;
}

function renderScene(data: Village, bannersOn = true, hallOn = bannersOn) {
  render(
    <I18nContext value={{ language: 'en', t: en, setLanguage: () => {} }}>
      <MemoryRouter>
        <VillageScene village={data} layout={COMPACT} bannersOn={bannersOn} hallOn={hallOn} />
      </MemoryRouter>
    </I18nContext>,
  );
  return screen.getByRole('group', { name: 'Buildings' });
}

const allBuilt = Object.fromEntries(buildingTypes.map((type) => [type, 1]));

describe('VillageScene', () => {
  afterEach(cleanup);

  it('makes every building one link to its screen, named with its level', () => {
    const scene = renderScene(village({ ...allBuilt, HEADQUARTERS: 12, SMITHY: 0 }));
    const links = within(scene).getAllByRole('link');
    expect(links).toHaveLength(buildingTypes.length);
    expect(within(scene).getByRole('link', { name: 'Town Hall, Lv 12' }).getAttribute('href')).toBe('/v/7/hq');
    expect(within(scene).getByRole('link', { name: 'Barracks, Lv 1' }).getAttribute('href')).toBe(
      '/v/7/recruit/barracks',
    );
    // not built: its place in the Town Hall
    expect(within(scene).getByRole('link', { name: 'Smithy, not built' }).getAttribute('href')).toBe('/v/7/hq#SMITHY');
    expect(within(scene).getByRole('link', { name: 'Wall, Lv 1' }).getAttribute('href')).toBe('/v/7/hq#WALL');
  });

  it('leaves the Banner Hall out on a world without banners and paladins unless one stands there', () => {
    renderScene(village({ ...allBuilt, BANNER_HALL: 0 }), false);
    expect(screen.queryByRole('link', { name: /Banner Hall/ })).toBeNull();
    expect(screen.queryByText('Banner Hall')).toBeNull();
    cleanup();

    renderScene(village({ ...allBuilt, BANNER_HALL: 2 }), false);
    expect(screen.getByRole('link', { name: 'Banner Hall, Lv 2' })).toBeTruthy();
    cleanup();

    renderScene(village({ ...allBuilt, BANNER_HALL: 0 }), true);
    expect(screen.getByRole('link', { name: 'Banner Hall, not built' })).toBeTruthy();
  });

  it('keeps the Banner Hall for the paladin on a world without banners and opens its recruiting tab', () => {
    renderScene(village({ ...allBuilt, BANNER_HALL: 0 }), false, true);
    expect(screen.getByRole('link', { name: 'Banner Hall, not built' })).toBeTruthy();
    cleanup();

    renderScene(village({ ...allBuilt, BANNER_HALL: 1 }), false, true);
    expect(screen.getByRole('link', { name: 'Banner Hall, Lv 1' }).getAttribute('href')).toBe(
      '/v/7/recruit/banner_hall',
    );
  });

  it('shows the target level and a progress bar on the building being upgraded', () => {
    const now = Date.now();
    const running = {
      id: 1,
      building: 'SMITHY' as const,
      targetLevel: 6,
      startAt: new Date(now - MINUTE).toISOString(),
      endAt: new Date(now + MINUTE).toISOString(),
      demolish: false,
      automated: false,
    };
    const waiting = { ...running, id: 2, building: 'MARKET' as const, targetLevel: 4, startAt: running.endAt };
    const scene = renderScene(village({ ...allBuilt, SMITHY: 5, MARKET: 3 }, [running, waiting]));
    expect(within(scene).getByText('5 → 6')).toBeTruthy();
    // waiting orders show nothing: the queue card lists them
    expect(within(scene).queryByText('3 → 4')).toBeNull();
    expect(within(scene).getByText('3')).toBeTruthy();
    const bars = scene.querySelectorAll('rect[class*="plateBar"]');
    expect(bars).toHaveLength(1);
    expect(Number(bars[0]?.getAttribute('width'))).toBeGreaterThan(0);
  });

  it('shows a dash on the plates of buildings not built', () => {
    const scene = renderScene(village({ HEADQUARTERS: 1 }));
    expect(within(scene).getAllByText('–')).toHaveLength(buildingTypes.length - 1);
  });
});

describe('VillageScene level pop', () => {
  afterEach(cleanup);

  function sceneOf(data: Village) {
    return (
      <I18nContext value={{ language: 'en', t: en, setLanguage: () => {} }}>
        <MemoryRouter>
          <VillageScene village={data} layout={COMPACT} bannersOn hallOn />
        </MemoryRouter>
      </I18nContext>
    );
  }

  it('pops a building whose level rose, not when a village opens or another one is shown', () => {
    const { container, rerender } = render(sceneOf(village({ ...allBuilt, HEADQUARTERS: 3 })));
    expect(container.querySelector('[class*="pop"]')).toBeNull();

    rerender(sceneOf(village({ ...allBuilt, HEADQUARTERS: 4 })));
    expect(container.querySelectorAll('[class*="pop"]')).toHaveLength(1);

    // another village: levels differ, nothing pops
    cleanup();
    const other = render(sceneOf(village({ ...allBuilt, HEADQUARTERS: 3 })));
    other.rerender(sceneOf({ ...village({ ...allBuilt, HEADQUARTERS: 9 }), id: 8 }));
    expect(other.container.querySelector('[class*="pop"]')).toBeNull();
  });
});

describe('palisade', () => {
  it('is a dashed oval (no stakes) while the wall is at level 0', () => {
    expect(palisade(WIDE, 0)).toBeUndefined();
  });

  it('draws stakes in front of and behind the buildings, with taller stakes as the wall grows', () => {
    const low = palisade(WIDE, 1);
    const high = palisade(WIDE, 20);
    expect(low?.back.front).toMatch(/^M/);
    expect(low?.front.front).toMatch(/^M/);
    const tip = (d: string | undefined) =>
      Math.min(...[...(d ?? '').matchAll(/L[\d.]+ ([\d.]+)/g)].map((m) => Number(m[1])));
    // the highest stake tip (smallest y) of the back half is higher on a grown wall
    expect(tip(high?.back.front)).toBeLessThan(tip(low?.back.front));
  });
});
