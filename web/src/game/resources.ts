/**
 * Live resource amounts (05-frontend §5). The server sends the stored amounts at a time `at` plus the
 * production per hour. Between two API calls the client projects them forward with the server's own
 * rule (02-game-rules §3, `Production.advance`), so the numbers tick up without asking the server.
 */

export const resourceTypes = ['wood', 'clay', 'iron'] as const;
export type ResourceType = (typeof resourceTypes)[number];

/** One number per resource, e.g. `{ wood: 812, clay: 790, iron: 655 }`. */
export type ResourceAmounts = Record<ResourceType, number>;

/**
 * What projectResources needs. The generated VillageDto has these fields (and more), so a VillageDto
 * can be passed in directly: TypeScript only checks that the shape fits, not the type's name.
 */
export interface ResourceSnapshot {
  resources: ResourceAmounts & { at: string };
  production: ResourceAmounts;
  storageCapacity: number;
}

const MS_PER_HOUR = 3_600_000;

/** The amounts at `nowMs`, fractional like on the server. Display them with Math.floor. */
export function projectResources(snapshot: ResourceSnapshot, nowMs: number): ResourceAmounts {
  const hours = Math.max(0, nowMs - Date.parse(snapshot.resources.at)) / MS_PER_HOUR;
  const capacity = snapshot.storageCapacity;
  const project = (type: ResourceType): number => {
    const stored = snapshot.resources[type];
    if (stored >= capacity) {
      return stored; // production never destroys resources above the capacity
    }
    return Math.min(capacity, stored + snapshot.production[type] * hours);
  };
  return { wood: project('wood'), clay: project('clay'), iron: project('iron') };
}

/** The warehouse is full for this resource (shown in a warning colour). */
export function isFull(amount: number, capacity: number): boolean {
  return Math.floor(amount) >= capacity;
}

/**
 * When the stock covers a cost. A tagged union: `kind` says which case it is, and TypeScript only
 * allows `atMs` after checking `kind === 'later'`.
 */
export type Affordability =
  | { kind: 'now' }
  /** at `atMs`, if nothing else is spent meanwhile */
  | { kind: 'later'; atMs: number }
  /** a cost is above the warehouse capacity: upgrade the warehouse first */
  | { kind: 'warehouse' }
  /** a missing resource isn't produced at all */
  | { kind: 'never' };

/** Like the server's check (02-game-rules §4.3): `floor(stock) ≥ cost` for each resource. */
export function whenAffordable(snapshot: ResourceSnapshot, cost: ResourceAmounts, nowMs: number): Affordability {
  const stock = projectResources(snapshot, nowMs);
  let waitMs = 0;
  for (const type of resourceTypes) {
    if (Math.floor(stock[type]) >= cost[type]) {
      continue;
    }
    if (cost[type] > snapshot.storageCapacity) {
      return { kind: 'warehouse' };
    }
    const perHour = snapshot.production[type];
    if (perHour <= 0) {
      return { kind: 'never' };
    }
    waitMs = Math.max(waitMs, ((cost[type] - stock[type]) / perHour) * MS_PER_HOUR);
  }
  return waitMs === 0 ? { kind: 'now' } : { kind: 'later', atMs: nowMs + Math.ceil(waitMs) };
}

/** When one resource hits the warehouse capacity (the warehouse page, M22). */
export type FullAt =
  | { kind: 'full' }
  /** at `atMs`, if nothing is spent and the production stays as it is */
  | { kind: 'at'; atMs: number }
  /** the resource isn't produced */
  | { kind: 'never' };

/** Like `projectResources`: production stops at the capacity, stock above it just stays. */
export function whenFull(snapshot: ResourceSnapshot, type: ResourceType, nowMs: number): FullAt {
  const stock = projectResources(snapshot, nowMs)[type];
  if (Math.floor(stock) >= snapshot.storageCapacity) {
    return { kind: 'full' };
  }
  const perHour = snapshot.production[type];
  if (perHour <= 0) {
    return { kind: 'never' };
  }
  return { kind: 'at', atMs: nowMs + Math.ceil(((snapshot.storageCapacity - stock) / perHour) * MS_PER_HOUR) };
}
