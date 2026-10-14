import { describe, expect, it } from 'vitest';
import { parseRecruitBuilding, recruitPath } from './recruitPaths';

describe('recruitPath', () => {
  it('puts the building in lower case into the URL', () => {
    expect(recruitPath(7, 'BARRACKS')).toBe('/v/7/recruit/barracks');
    expect(recruitPath(7, 'ACADEMY')).toBe('/v/7/recruit/academy');
    expect(recruitPath(7, 'BANNER_HALL')).toBe('/v/7/recruit/banner_hall');
  });
});

describe('parseRecruitBuilding', () => {
  it('reads the recruiting buildings', () => {
    expect(parseRecruitBuilding('stable')).toBe('STABLE');
    expect(parseRecruitBuilding('workshop')).toBe('WORKSHOP');
    // M32b: banner thieves are trained in the Banner Hall
    expect(parseRecruitBuilding('banner_hall')).toBe('BANNER_HALL');
  });

  it('rejects everything else', () => {
    expect(parseRecruitBuilding(undefined)).toBeUndefined();
    expect(parseRecruitBuilding('STABLE')).toBeUndefined();
    expect(parseRecruitBuilding('smithy')).toBeUndefined();
    expect(parseRecruitBuilding('')).toBeUndefined();
  });
});
