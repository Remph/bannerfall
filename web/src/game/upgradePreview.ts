import type { BuildingEffect, BuildingInfo } from '../api/queries/buildings';
import type { Language } from '../i18n/language';
import { formatDecimal, formatNumber, formatShare } from './format';
import { whenAffordable, type ResourceSnapshot } from './resources';

/**
 * Why the next level of a building can't be queued right now (the Town Hall preview, M24). A tagged
 * union like `Affordability`: `kind` says which case it is. The order is the order of usefulness –
 * waiting for resources doesn't help while the farm is too small.
 */
export type UpgradeBlocker =
  /** a Banner Hall while the world has banners switched off (the server says BANNERS_DISABLED, D11) */
  | { kind: 'bannersDisabled' }
  /** a demolition of the building is queued (1.6): a building has upgrades or demolitions queued, not both */
  | { kind: 'demolition' }
  /** the requirements of the next level are not met yet (queued levels count) */
  | { kind: 'requirements'; missing: BuildingInfo['missing'] }
  | { kind: 'queueFull' }
  /** a cost is above the warehouse capacity */
  | { kind: 'warehouse' }
  /** a missing resource isn't produced */
  | { kind: 'never' }
  | { kind: 'population' }
  /** enough resources at `atMs`, if nothing else is spent meanwhile */
  | { kind: 'later'; atMs: number };

/**
 * What stands between the player and the next level of `info`, or null if it can be queued now (or
 * there is no next level). Mirrors the checks of POST …/build-orders, in the order the Town Hall
 * has always shown them. `hallAvailable`: whether the world lets a Banner Hall be built (banners or
 * paladins on, `bannerHallAvailable`).
 */
export function upgradeBlocker(
  info: BuildingInfo,
  village: ResourceSnapshot,
  freePop: number,
  queueFull: boolean,
  nowMs: number,
  hallAvailable: boolean,
): UpgradeBlocker | null {
  const next = info.next;
  if (next === null) {
    return null;
  }
  // First, unlike on the server: requirements, a free slot or resources would never make it possible,
  // only the host switching banners or paladins on again.
  if (info.building === 'BANNER_HALL' && !hallAvailable) {
    return { kind: 'bannersDisabled' };
  }
  if (info.queuedLevel < info.level) {
    return { kind: 'demolition' };
  }
  if (!info.requirementsMet) {
    return { kind: 'requirements', missing: info.missing };
  }
  if (queueFull) {
    return { kind: 'queueFull' };
  }
  const affordable = whenAffordable(village, next.cost, nowMs);
  if (affordable.kind === 'warehouse') {
    return { kind: 'warehouse' };
  }
  if (affordable.kind === 'never') {
    return { kind: 'never' };
  }
  if (next.pop > freePop) {
    return { kind: 'population' };
  }
  return affordable.kind === 'later' ? { kind: 'later', atMs: affordable.atMs } : null;
}

/** An effect's before and after, formatted; `from` is null for an effect the building doesn't have yet. */
export interface FormattedEffect {
  from: string | null;
  to: string;
}

/** 5.5 → "5.5", 546.15 → "546": production keeps a decimal while the numbers are small. */
function formatProduction(value: number, language: Language): string {
  return value < 100 ? formatDecimal(value, language) : formatNumber(value, language);
}

/**
 * The numbers of a building effect as text. The unit of each kind is part of the number (a
 * percentage) or of the label (per hour), so the two come out as "30.0 → 34.9".
 */
export function formatEffect(effect: BuildingEffect, language: Language): FormattedEffect {
  const format = (value: number): string => {
    switch (effect.kind) {
      case 'PRODUCTION_PER_HOUR':
        return formatProduction(value, language);
      case 'BUILD_TIME_PERCENT':
      case 'RECRUIT_TIME_PERCENT':
      case 'WALL_DEFENSE_PERCENT':
        return formatShare(value, language);
      case 'WAREHOUSE_CAPACITY':
      case 'FARM_CAPACITY':
      case 'HIDING_CAPACITY':
      case 'MERCHANTS':
      case 'WALL_DEFENSE_FLAT':
        return formatNumber(value, language);
    }
  };
  return { from: effect.from === null ? null : format(effect.from), to: format(effect.to) };
}
