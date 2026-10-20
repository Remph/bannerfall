import { describe, expect, it } from 'vitest';
import {
  bannerHallPath,
  desktopSections,
  moreGroups,
  navEntry,
  phoneTabs,
  sections,
  visibleSections,
} from './sections';

describe('visibleSections', () => {
  it('hides the guild and the Banner Hall while the host has them off', () => {
    expect(visibleSections(desktopSections, { guild: false, bannerHall: false })).toEqual([
      'village',
      'townHall',
      'recruit',
      'market',
      'map',
      'rallyPoint',
      'farm',
      'reports',
      'messages',
      'ranking',
      'achievements',
    ]);
  });

  it('puts the Banner Hall after Recruit when it is on', () => {
    const keys = visibleSections(desktopSections, { guild: true, bannerHall: true });
    expect(keys.indexOf('bannerHall')).toBe(keys.indexOf('recruit') + 1);
    expect(keys.at(-1)).toBe('guild');
  });
});

describe('the navigation', () => {
  it('lists every section once, on desktop and on the phone', () => {
    const all = Object.keys(sections).sort();
    expect([...desktopSections].sort()).toEqual(all);
    expect([...phoneTabs, ...moreGroups.village, ...moreGroups.player].sort()).toEqual(all);
  });

  it('groups More like the desktop: the village pages, then the player pages', () => {
    expect(moreGroups.village.every((key) => sections[key].global !== true)).toBe(true);
    expect(moreGroups.player.every((key) => sections[key].global === true)).toBe(true);
  });

  it('opens the Banner Hall of the current village', () => {
    const villageId = 7;
    expect(sections.bannerHall.path?.(villageId)).toBe(`/v/${villageId}/banners`);
  });

  it('opens the hall tab of the recruit screen in a world without banners, like the building does', () => {
    expect(bannerHallPath(7, true)).toBe('/v/7/banners');
    expect(bannerHallPath(7, false)).toBe('/v/7/recruit/banner_hall');
  });
});

describe('navEntry: what a page lights in the navigation (1.5.1)', () => {
  const ME = 7;
  const entry = (pathname: string, bannersOn = true) => navEntry(pathname, ME, bannersOn);

  it('lights the section of every page that has one', () => {
    expect(entry('/v/3')).toBe('village');
    expect(entry('/v/3/hq')).toBe('townHall');
    expect(entry('/v/3/recruit')).toBe('recruit');
    expect(entry('/v/3/recruit/stable')).toBe('recruit');
    expect(entry('/v/3/banners')).toBe('bannerHall');
    expect(entry('/v/3/map')).toBe('map');
    expect(entry('/v/3/rally')).toBe('rallyPoint');
    expect(entry('/v/3/farm')).toBe('farm');
    expect(entry('/v/3/market')).toBe('market');
    expect(entry('/reports')).toBe('reports');
    expect(entry('/messages')).toBe('messages');
    expect(entry('/ranking')).toBe('ranking');
    expect(entry('/achievements')).toBe('achievements');
    expect(entry('/guild')).toBe('guild');
  });

  it('lights the parent of a page without an entry, where its back link leads', () => {
    expect(entry('/v/3/warehouse')).toBe('village');
    expect(entry('/v/3/smithy')).toBe('village');
    expect(entry('/v/3/rally/simulator')).toBe('rallyPoint');
    expect(entry('/reports/12')).toBe('reports');
    expect(entry('/messages/4')).toBe('messages');
    expect(entry('/statistics')).toBe('ranking');
    expect(entry('/players/8')).toBe('ranking');
    expect(entry('/guilds/2')).toBe('guild');
  });

  it('lights the own profile entry on the own profile, not the ranking', () => {
    expect(entry(`/players/${ME}`)).toBe('profile');
    expect(entry(`/players/${ME}0`)).toBe('ranking');
  });

  it('lights the Banner Hall on the recruit screen hall tab in a world without banners', () => {
    expect(entry('/v/3/recruit/banner_hall', false)).toBe('bannerHall');
    expect(entry('/v/3/recruit/banner_hall', true)).toBe('recruit');
  });

  it('marks the pages of the settings menu, and nothing for anything else', () => {
    for (const path of ['/notifications', '/password', '/about', '/privacy', '/admin']) {
      expect(entry(path)).toBe('settings');
    }
    expect(entry('/')).toBeUndefined();
    expect(entry('/v/3/nowhere')).toBeUndefined();
  });
});
