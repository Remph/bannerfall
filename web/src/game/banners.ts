/** Banners on the client (M32a, 02-game-rules §21): the server decides, this only reads its numbers. */
import { bannerTypes, type BannerType } from './gameData';

/** What the Banner Hall page needs of one banner. */
export interface BannerLike {
  id: number;
  type: BannerType;
  tier: number;
  villageId: number | null;
}

/** I, II, III: how the tiers are written (they have no words of their own, so both languages use it). */
export function tierName(tier: number): string {
  return ['I', 'II', 'III'][tier - 1] ?? String(tier);
}

/** The tier a banner works at: the Banner Hall caps it (§21.2). 0 = dormant (no hall, or no banner). */
export function effectiveTier(tier: number, hallLevel: number): number {
  return Math.max(0, Math.min(tier, hallLevel));
}

/** The effect of a tier as a fraction (0.1 = 10 %); 0 for tier 0 or a tier the table does not have. */
export function effectOfTier(effects: Partial<Record<BannerType, readonly number[]>>, type: BannerType, tier: number) {
  return tier < 1 ? 0 : (effects[type]?.[tier - 1] ?? 0);
}

/** 0.1 → 10, 0.045 → 4.5: the percent a text shows (at most one decimal). */
export function percentPoints(fraction: number): number {
  return Math.round(fraction * 1000) / 10;
}

/** The banners in the stash (not flying anywhere): highest tier first, then in the order of the types. */
export function stashOf<T extends BannerLike>(banners: readonly T[]): T[] {
  return banners
    .filter((banner) => banner.villageId === null)
    .sort((a, b) => b.tier - a.tier || bannerTypes.indexOf(a.type) - bannerTypes.indexOf(b.type) || a.id - b.id);
}

/**
 * The stash for its list (1.3): banners of the same type and tier in one row with their count, in
 * stash order. The row's banner is the first of them, the one "Equip" sends.
 */
export function groupStash<T extends BannerLike>(stash: readonly T[]): { banner: T; count: number }[] {
  const groups: { banner: T; count: number }[] = [];
  for (const banner of stash) {
    const group = groups.find((entry) => entry.banner.type === banner.type && entry.banner.tier === banner.tier);
    if (group === undefined) {
      groups.push({ banner, count: 1 });
    } else {
      group.count += 1;
    }
  }
  return groups;
}

/** Banners of one tier that fuse into one of the next tier (02-game-rules §21.4.2, banners.yml). */
export const FUSE_COUNT = 3;
/** The highest tier: it can't be fused. */
export const MAX_TIER = 3;

/** The stash banners that can be fused, per tier (I and II), each list in stash order. */
export function fusableByTier<T extends BannerLike>(stash: readonly T[]): { tier: number; banners: T[] }[] {
  const tiers: { tier: number; banners: T[] }[] = [];
  for (let tier = 1; tier < MAX_TIER; tier++) {
    tiers.push({ tier, banners: stash.filter((banner) => banner.tier === tier && banner.villageId === null) });
  }
  return tiers;
}

/**
 * Picks or drops a banner for fusing: a banner of another tier starts a new choice, and no more than
 * {@link FUSE_COUNT} can be chosen.
 */
export function toggleFuseChoice(chosen: readonly BannerLike[], banner: BannerLike): BannerLike[] {
  if (chosen.some((entry) => entry.id === banner.id)) {
    return chosen.filter((entry) => entry.id !== banner.id);
  }
  if (chosen.length > 0 && chosen[0]?.tier !== banner.tier) {
    return [banner];
  }
  return chosen.length >= FUSE_COUNT ? [...chosen] : [...chosen, banner];
}

/** The types the fused banner can take: those of the chosen banners, each once, in the order of the types. */
export function fuseTypes(chosen: readonly BannerLike[]): BannerType[] {
  return bannerTypes.filter((type) => chosen.some((banner) => banner.type === type));
}

/**
 * The tier whose effect is `fraction` (a report says "+6 %", the simulator wants the tier); 0 for no
 * effect or a number the table doesn't have.
 */
export function tierOfEffect(
  effects: Partial<Record<BannerType, readonly number[]>>,
  type: BannerType,
  fraction: number | null | undefined,
): number {
  if (fraction === null || fraction === undefined || fraction <= 0) {
    return 0;
  }
  const index = (effects[type] ?? []).findIndex((value) => Math.abs(value - fraction) < 1e-9);
  return index + 1;
}
