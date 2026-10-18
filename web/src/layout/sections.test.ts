import { describe, expect, it } from 'vitest';
import { bannerHallPath, desktopSections, moreGroups, phoneTabs, sections, visibleSections } from './sections';

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
