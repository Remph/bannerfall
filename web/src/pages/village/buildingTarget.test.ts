import { describe, expect, it } from 'vitest';
import { buildingTarget } from './buildingTarget';

describe('buildingTarget', () => {
  it('opens the own screen of a built building that has one', () => {
    expect(buildingTarget(7, 'BARRACKS', 3)).toEqual({ pathname: '/v/7/recruit/barracks' });
    expect(buildingTarget(7, 'STABLE', 1)).toEqual({ pathname: '/v/7/recruit/stable' });
    expect(buildingTarget(7, 'WORKSHOP', 1)).toEqual({ pathname: '/v/7/recruit/workshop' });
    expect(buildingTarget(7, 'ACADEMY', 1)).toEqual({ pathname: '/v/7/recruit/academy' });
    expect(buildingTarget(7, 'RALLY_POINT', 1)).toEqual({ pathname: '/v/7/rally' });
    expect(buildingTarget(7, 'MARKET', 2)).toEqual({ pathname: '/v/7/market' });
    expect(buildingTarget(7, 'WAREHOUSE', 5)).toEqual({ pathname: '/v/7/warehouse' });
    expect(buildingTarget(7, 'SMITHY', 1)).toEqual({ pathname: '/v/7/smithy' });
    expect(buildingTarget(7, 'BANNER_HALL', 2)).toEqual({ pathname: '/v/7/banners' });
  });

  it('opens the recruiting tab of the Banner Hall in a world without banners (1.4: it trains the paladin)', () => {
    expect(buildingTarget(7, 'BANNER_HALL', 1, false)).toEqual({ pathname: '/v/7/recruit/banner_hall' });
  });

  it('opens the Town Hall itself without a highlight', () => {
    expect(buildingTarget(7, 'HEADQUARTERS', 5)).toEqual({ pathname: '/v/7/hq' });
  });

  it('sends buildings without a screen, and buildings not built yet, to their place in the Town Hall', () => {
    expect(buildingTarget(7, 'WOODCUTTER', 4)).toEqual({ pathname: '/v/7/hq', hash: 'WOODCUTTER' });
    expect(buildingTarget(7, 'SMITHY', 0)).toEqual({ pathname: '/v/7/hq', hash: 'SMITHY' });
    expect(buildingTarget(7, 'BARRACKS', 0)).toEqual({ pathname: '/v/7/hq', hash: 'BARRACKS' });
    expect(buildingTarget(7, 'MARKET', 0)).toEqual({ pathname: '/v/7/hq', hash: 'MARKET' });
    expect(buildingTarget(7, 'RALLY_POINT', 0)).toEqual({ pathname: '/v/7/hq', hash: 'RALLY_POINT' });
    expect(buildingTarget(7, 'WAREHOUSE', 0)).toEqual({ pathname: '/v/7/hq', hash: 'WAREHOUSE' });
    expect(buildingTarget(7, 'BANNER_HALL', 0)).toEqual({ pathname: '/v/7/hq', hash: 'BANNER_HALL' });
  });
});
