# Bannerfall – 02 Game Rules

The rulebook. Every formula the server needs is defined here **exactly**: rounding, edge cases and order of operations. If code and this file disagree, fix one of them in the same branch.

All numbers are **first drafts**. They are inspired by the genre but are Bannerfall's own values. They live in config (`server/src/main/resources/gamedata/*.yml` and the `world_settings` table), so balancing never needs a code change.

## Conventions

- `round(x)` = round half up = `Math.round(x)` for x ≥ 0 (Java). Used everywhere unless a formula says `floor` or `ceil`.
- Times are `Instant` (UTC) on the server, durations in whole seconds unless stated.
- Resources are `double` internally (lazy production produces fractions). Everything shown to players and every cost check uses `floor(amount)`.
- `speed` and `unitSpeed` are world settings (see §1).
- "Level L" of a building means its current level. Upgrade costs and times are always "for reaching level L".

---

## 1. World settings

One world per deployment (a new world = a fresh database). Stored in the single row of `world_settings`.

| Setting | Default | Meaning |
|---|---|---|
| `name` | "Bannerfall 1" | Display name |
| `speed` | 1.0 | Multiplies production and loyalty regen, divides build/recruit times |
| `unitSpeed` | 1.0 | Additionally divides travel times (troops and merchants) |
| `mapSize` | 100 | Coordinates are `0 ≤ x, y < mapSize`. Small on purpose: 2–10 players plus NPC lords should meet each other early |
| `startResources` | 500 | Wood, clay, iron in a new player village |
| `luckRange` | 0.25 | Luck is uniform in `[-luckRange, +luckRange]` |
| `beginnerProtectionHours` | 72 | Not scaled by speed |
| `nobleMaxDistance` | 70 | Fields |
| `loyaltyRegenPerHour` | 1.0 | × `speed` |
| `buildQueueSize` | 5 | Build orders per village at the normal price (incl. the running one; 3 until M13c) |
| `buildQueueMax` | 10 | Max build orders per village: the places after `buildQueueSize` are paid slots with a surcharge (§4.3, M13c); never below `buildQueueSize` |
| `recruitQueueSize` | 5 | Max recruit orders per building per village |
| `cancelWindowSeconds` | 600 | Commands can be cancelled this long after departure |
| `barbarianInitialCount` | 30 | Spawned at world creation |
| `barbariansPerNewPlayer` | 3 | Spawned around each new player |
| `barbarianGrowthEnabled` | true | See §11 |
| `barbarianWallsEnabled` | false | Barbarians build walls (M29, §11): off = a new barbarian village starts with a random wall 0–2 and never builds one (the old behaviour); on = it starts without a wall and the growth round builds one |
| `barbarianWallStartDays` | 3 | 0–365 real days after the world opened before the walls start to grow (not scaled by `speed`, like the beginner protection) |
| `barbarianWallMaxLevel` | 5 | 0–20: the highest wall level barbarians build |
| `barbarianWallsRebuild` | false | A wall that rams knocked down grows back (otherwise it stays down) |
| `unitLevelsEnabled` | false | Unit levels (M30, §5.4): on = the smithy can raise units to level 2 and 3; off = every unit has the table's values (§5.1), exactly as before. The smithy page shows the unit values either way |
| `paladinVillagesPerSlot` | 4 | 1–100: villages a player owns for each further paladin slot (M31, §5.5): the second slot opens at 1 + this many villages (5 with 4), the third at 1 + 2× this (9) … |
| `paladinMaxSlots` | 5 | 0–20: the most paladin slots a player can have (M31, §5.5). **0 = no paladin in this world**: the barracks does not list him, nothing can be recruited |
| `bannersEnabled` | true | Banners (M32, §21): on = the Banner Hall, equipping and the banner effects work; off = no effects, nothing can be equipped or built (the Hall is hidden), banners that exist are kept and asleep |
| `bannerDropFactor` | 1.0 | 0–3: multiplies the banner drop chances (§21.7, built in M32b); 0 = no drops. Stored and shown since M32a |
| `tradeMaxRatio` | 3.0 | Market offers: `1/ratio ≤ want/offer ≤ ratio` |
| `npcLordCount` | 4 | NPC lords spawned at world creation (§11b) |
| `npcDifficulty` | NORMAL | EASY / NORMAL / HARD / CUSTOM – the NPC balance limits (§11b, 09 §8); CUSTOM's values are edited by the admin |
| `npcThinkMinutes` | 20 | Base interval between NPC decision rounds, ÷ `speed` (§11b) |
| `inviteCode` | (set by admin) | Required for registration; friends only |

The admin changes everything in this table except `mapSize`, `barbarianInitialCount` and `npcLordCount` (only used when the world is created) in a running world (M14a, `PUT /api/admin/world-settings`, ranges in 04-api); the cached settings reload at once. Running events keep their times, and a new `speed` counts for production from each village's last update on (resources are computed lazily from that anchor), so change it early or accept a small jump. `npcDifficulty` has its own endpoint.

**Effect of `speed`:** production ×speed, build time ÷speed, recruit time ÷speed, travel time ÷(speed × unitSpeed), loyalty regen ×speed, barbarian growth interval ÷speed, NPC think interval ÷speed. Beginner protection is NOT scaled.

First friends world (2026-10-11): `speed = 6`, `unitSpeed = 1`, `beginnerProtectionHours = 36` (about a week of play should reach the NPC lords' nobles and first conquests; a few days do not).

### 1b. Pre-game (M14g)

A fresh production world starts in the **pre-game** (`bannerfall.world.start-in-pregame`, on in the `prod` profile; dev and tests start an open world as before). The world's content does not exist yet: no map, no barbarians, no NPC lords, no villages.

- **Players** can register (invite code as always), log in, chat, change language, theme and password, and read the world settings. They have **no village**: `GET /api/me` has `villages: []` and `needsRespawn: false`, the web shows the countdown and the settings instead of the village. Every other game request is answered `409 WORLD_NOT_OPEN` (`PregameGateFilter`, a short list of what stays open: auth, `/api/me`, password, `/api/world`, chat, admin). A later feature that works before the opening (guilds: creating one, if the host enabled them) adds its path to that list.
- **The host** (admin page, tab World, or `bannerfall-admin world open`) configures everything, including `mapSize`, `barbarianInitialCount` and `npcLordCount`, which are fixed once the world is open, and sets the **opening time** (any time, movable, can be taken back; a time in the past or "now" opens within a moment). Without a time the world waits.
- **At the opening time** (event `WORLD_OPEN`, survives restarts like every event): the content is created exactly like at the first start of an open world (02 §11, §11b), then every player who registered meanwhile gets a starting village, oldest registration first, with `barbariansPerNewPlayer` barbarian villages around it. **Beginner protection counts from the opening**, not from the registration. A player the map has no room for stays without a village and sees the respawn screen, which tries again. Everybody online gets `WORLD_OPENED` and the page switches over by itself. A registration after the opening works as before (village at once).
- Banned-for-good and deleted accounts get no village. Spawning a barbarian village or an NPC lord by hand is refused in the pre-game.
- **Not built (2026-10-11):** a mailing list (start notice by e-mail, double opt-in, SMTP) — dropped for now; friends who register before the start are the list. Revisit before a public launch (own session).

---

## 2. Map and placement

- Fields have integer coordinates `(x|y)`. Center `c = mapSize / 2` (integer division).
- **Distance** (fields, double): `d = sqrt((x1−x2)² + (y1−y2)²)`.
- At most one village per field.
- **Spacing rule:** a new village may not be placed within distance `< 2` of another village (neighbouring fields stay free).

**Player spawn** (`MapPlacement.findPlayerSpot`). The new village is named `<player name>'s village` (just the player name if that is longer than 32 characters); players can rename it.
```
n = number of player-owned villages
r = 8 + 2.5 * sqrt(n) + random(-2.0, 2.0)
repeat up to 200 times:
    angle = random(0, 2π)
    (x, y) = (round(c + r*cos(angle)), round(c + r*sin(angle)))
    if inside map and spacing rule ok: return (x, y)
    r = r + 0.25
fail -> no spot (MapPlacement returns Optional.empty(); the service answers WORLD_FULL)
```
**Barbarian spawn around a point** `(px, py)`: same loop with `r = random(3, 8)` around `(px, py)`.
**Initial barbarians:** `barbarianInitialCount` villages with the loop around the center, `r = random(4, 30)`.

`Random` is injected (`RandomSource` interface), so tests are deterministic.

---

## 3. Resources

Three resources: **wood, clay, iron**. Produced by WOODCUTTER, CLAY_PIT, IRON_MINE respectively.

| Quantity | Formula |
|---|---|
| Production per hour at level L | `L == 0 ? 5 : 30 × 1.165^(L−1)`, then × `speed` × owner's production factor (1.0 for humans and barbarians; NPC lords see §11b) – not rounded |
| Warehouse capacity (per resource) | `round(1000 × 1.229^(L−1))` (L ≥ 1) |
| Farm population capacity | `round(240 × 1.172^(L−1))` (L ≥ 1) |
| Hiding place (per resource, protected from loot) | `L == 0 ? 0 : round(150 × 1.334^(L−1))` |

**Banners (M32a, §21.2).** A **Harvest** banner multiplies the production of all three resources of its village by `1 + bonus`; a **Treasury** banner raises the warehouse and the hiding place capacity to `round(capacity × (1 + bonus))`. Both change what a village makes and holds, so the village is caught up first when a banner is equipped or taken down.

**When a resource is full (M22, UI only).** `stock + production/h × t` reaches the capacity after `(capacity − stock) / production` hours; production never destroys what is above the capacity (it just stays). The warehouse page (05 §3) shows this per resource, assuming nothing is spent and the production stays as it is.

Reference values (speed 1):

| L | prod/h | warehouse | farm | hiding |
|---|---|---|---|---|
| 0 | 5 | – | – | 0 |
| 1 | 30.00 | 1 000 | 240 | 150 |
| 5 | 55.26 | 2 281 | 453 | 475 |
| 10 | 118.59 | 6 397 | 1 001 | 2 007 |
| 20 | 546.15 | 50 290 | 4 896 | – |
| 30 | 2 515.20 | 395 371 | 23 939 | – |

**Lazy production** (`Production.advance`), for each resource:
```
dtHours = max(0, t − resourcesUpdatedAt) in hours (double)
if stored >= cap:  stored unchanged          // never destroy resources by production
else:              stored = min(cap, stored + prodPerHour × dtHours)
resourcesUpdatedAt = max(resourcesUpdatedAt, t)
```
**Incoming resources** (returning loot, trade arrivals): `stored = max(stored, min(cap, stored + amount))`. Anything above capacity is lost.

---

## 4. Buildings

### 4.1 Table

`pop` = population base, `time` = base build time in seconds, `factor` = cost factor, `pts` = points base. "Start" = level in a new player village.

| Key | EN | DE | Max | Start | Wood | Clay | Iron | Factor | Pop | Time (s) | Pts | Requires |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| HEADQUARTERS | Town Hall | Rathaus | 30 | 1 | 90 | 80 | 70 | 1.26 | 5 | 900 | 10 | – |
| BARRACKS | Barracks | Kaserne | 25 | 0 | 200 | 170 | 90 | 1.26 | 7 | 1800 | 16 | HQ 3 |
| STABLE | Stable | Stall | 20 | 0 | 270 | 240 | 260 | 1.26 | 8 | 6000 | 20 | HQ 10, Barracks 5, Smithy 5 |
| WORKSHOP | Workshop | Werkstatt | 15 | 0 | 300 | 240 | 260 | 1.26 | 8 | 6000 | 24 | HQ 10, Smithy 10 |
| ACADEMY | Academy | Akademie | 1 | 0 | 15000 | 25000 | 10000 | 1.0 | 80 | 64800 | 512 | HQ 20, Smithy 20, Market 10 |
| SMITHY | Smithy | Schmiede | 20 | 0 | 220 | 180 | 240 | 1.26 | 20 | 6000 | 19 | HQ 5, Barracks 1 |
| RALLY_POINT | Rally Point | Sammelplatz | 1 | 1 | 10 | 40 | 30 | 1.0 | 0 | 600 | 0 | – |
| MARKET | Market | Marktplatz | 25 | 0 | 100 | 100 | 100 | 1.26 | 20 | 2700 | 10 | HQ 3, Warehouse 2 |
| WOODCUTTER | Woodcutter | Holzfäller | 30 | 1 | 50 | 60 | 40 | 1.25 | 5 | 900 | 6 | – |
| CLAY_PIT | Clay Pit | Lehmgrube | 30 | 1 | 65 | 50 | 40 | 1.27 | 10 | 900 | 6 | – |
| IRON_MINE | Iron Mine | Eisenmine | 30 | 1 | 75 | 65 | 70 | 1.252 | 10 | 1080 | 6 | – |
| FARM | Farm | Bauernhof | 30 | 1 | 45 | 40 | 30 | 1.30 | 0 | 1200 | 5 | – |
| WAREHOUSE | Warehouse | Speicher | 30 | 1 | 60 | 50 | 40 | 1.265 | 0 | 1020 | 6 | – |
| HIDING_PLACE | Hiding Place | Versteck | 10 | 0 | 50 | 60 | 50 | 1.25 | 2 | 1800 | 5 | – |
| WALL | Wall | Wall | 20 | 0 | 50 | 100 | 20 | 1.26 | 5 | 3600 | 8 | Barracks 1 |
| BANNER_HALL | Banner Hall | Bannerhalle | 3 | 0 | 300 | 350 | 250 | 1.5 | 8 | 7200 | 20 | HQ 8, Barracks 5 |

### 4.2 Formulas

| Quantity | Formula |
|---|---|
| Cost to reach level L (each resource) | `round(base × factor^(L−1))` |
| Population used by a building at level L | `L == 0 ? 0 : round(popBase × 1.17^(L−1))` |
| Extra population needed for an upgrade to L | `popTotal(L) − popTotal(L−1)` |
| Build time to reach level L | `max(1, round(baseTime × 1.18^(L−1) × 0.952^hqLevel / speed))` |
| Points of a building at level L | `Σ_{l=1..L} round(ptsBase × 1.2^(l−1))` |
| Village points | sum over all buildings |
| Player points | sum over the player's villages |

`hqLevel` = the HQ level **at the moment the order is enqueued** (simplification: a queued HQ upgrade does not speed up orders already in the queue).

A **Builders** banner (M32a, §21.2) shortens the time of every **new** build order: `max(1, round(time × (1 − bonus)))`, applied once when the order is queued (a queued order keeps its time, like the HQ level). Costs are unchanged.

Reference: HQ level 2 with HQ 1 → 1011 s. Woodcutter 30 with HQ 20 → 40 885 s (≈ 11.4 h at speed 1).

### 4.3 Build queue rules

- Max `buildQueueMax` orders per village (10), including the running one. Places 1 to `buildQueueSize` (5; the running order is place 1) cost the normal price; the **paid slots** behind them cost more (M13c, 2026-10-07).
- **Surcharge** of an order queued at place `p > buildQueueSize`: `25 % × 2^(p − buildQueueSize − 1)` on each resource, `round(cost × (100 + surcharge) / 100)` → place 6 +25 %, 7 +50 %, 8 +100 %, 9 +200 %, 10 +400 % (the doubling after 100 % confirmed at the start of M13c, 2026-10-07). Build time and population are not affected. The place is the one the order takes when it is enqueued (= queue length + 1); the surcharge is paid with the cost, stored with it (`cost_*` of the order) and refunded with it on a cancel (capped like every refund). An order that later moves up into the normal places keeps what it paid.
- **No moving in the paid slots:** an order on a place > `buildQueueSize` can't be moved, and nothing can be moved to such a place (`ORDER_NOT_MOVABLE`). Once earlier orders are done and it stands on a place ≤ `buildQueueSize`, it moves like any other.
- Orders run **sequentially** per village: `startAt = max(now, endAt of last order)`, `endAt = startAt + buildTime`.
- Target level of a new order = current level + number of queued orders for the same building + 1. Must be ≤ max level.
- Requirements (table column "Requires") are checked against **current levels plus queued levels**.
- **Costs and population are paid at enqueue.** Check: `floor(stored) ≥ cost` for each resource, and `freePop ≥ extra pop` (see §5.3).
- When an order completes (event `BUILD_COMPLETE` at `endAt`): first `catchUp(endAt)` (production at the old level up to that moment), then level += 1, then recompute village and player points.
- **Cancel** (any order that is not done yet, owner only; an order whose `endAt` has passed counts as done even if the worker hasn't applied it yet). Later orders that no longer fit are cancelled with it: later upgrades of the same building (their target level is gone) and orders whose requirements were only met through a cancelled order; the player can queue them again. Every cancelled order is refunded (resources and population), but resources are **received like incoming resources** (§3): `max(stored, min(cap, stored + refund))`, the part above the capacity is lost. The orders that stay shift earlier: each one gets `startAt = min(its old startAt, max(now, endAt of the previous remaining order))` with its stored duration, and its event is rescheduled. So an order is never pushed later by a cancel.
- **Move** (M4c, 2026-10-03): the owner can move a queued order that hasn't started to another place behind the orders that have started, as long as every order still fits afterwards (it is the next level of its building and its requirements are met by the orders in front of it). So upgrades of one building keep their order, and nothing moves in front of what it needs. An order has started once its `startAt` is reached (normally only the running one; more while the event worker is behind). Durations and costs stay; the waiting orders are re-timed back to back after the started ones (`startAt = max(now, end of the previous order)`) and the events move with them (here an order may get later).
- The build time is fixed at enqueue (Town Hall level of that moment, stored as `durationSeconds`); later Town Hall levels don't change orders already in the queue.
- No demolishing in the MVP.

### 4.4 Upgrade preview (M24, branch `integration/v1-1-features`)

The Town Hall page shows what the **next** level of a building does before the player queues it (hover or focus on a computer, an info button elsewhere, 05 §3). Nothing in the game changes: every number comes from the functions the game itself uses (`rules.BuildingEffects`, which calls `Production.perHour`, `Storage`, `Population.farmCapacity`, `Merchants.total` and the build and recruit time factors), so the preview can't drift from the rules. The level compared is the **queued** level (current plus queued orders) and the one after it, like `next` in the building list.

| Building | What changes (before → after) |
|---|---|
| Woodcutter, Clay Pit, Iron Mine | production per hour (with the world speed) |
| Warehouse | capacity per resource |
| Farm | population limit |
| Hiding Place | resources per type hidden from raiders |
| Market | merchants |
| Town Hall | build time of new orders as a share of the base time: `0.952^level` (level 0 = 100 %) |
| Barracks, Stable, Workshop | recruit time per unit as a share of level 1: `0.937^(level−1)` (no "before" for a building that isn't built) |
| Wall | defence bonus in percent (`5 × level`) and flat defence (`50 × level`), the two parts of the battle formula in §8 |
| Smithy, Academy, Rally Point | nothing by themselves; others need them |

**Queued levels and paid slots.** Effects, unlocks, cost and population all start from the queued level, so two queued woodcutter levels make the preview show level 3 → 4 of the production. The cost is the price of the next free place of the queue: from the first paid slot on it includes the surcharge (02 §4.3), and the preview says so ("The next order takes a paid slot: it costs 25% more."). The build time uses the Town Hall level of the moment, like a real order; a queued Town Hall level only changes the time of orders placed after it is done, which is what the Town Hall's own preview shows (before → after, from its queued level).

**Unlocks.** The preview also names what the upgrade makes available: the buildings and the units whose requirements are not met with the queued levels but are met with one more level of this building. A level that is only one of several missing requirements unlocks nothing yet.

**Why not.** When the next level can't be queued the preview says why instead of hiding it, with the reasons in this order: requirements missing (queued levels count), the build queue is full, a cost is above the warehouse capacity, a needed resource isn't produced, the farm has no room, "enough resources at 21:14". At the maximum level it says so. Buildings whose requirements are not met show the same preview with what they need.

---

## 5. Units

### 5.1 Table

`type` decides which defence value is used against it (§8). Speed is minutes per field.

| Key | EN | DE | Building | Requires | Wood | Clay | Iron | Pop | Att | Def inf | Def cav | Type | Speed | Carry | Time (s) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| SPEARMAN | Spearman | Speerträger | BARRACKS | Barracks 1 | 50 | 30 | 10 | 1 | 10 | 15 | 40 | INF | 18 | 25 | 1000 |
| SWORDSMAN | Swordsman | Schwertkämpfer | BARRACKS | Barracks 1, Smithy 1 | 30 | 30 | 70 | 1 | 20 | 50 | 20 | INF | 22 | 15 | 1450 |
| AXEMAN | Axeman | Axtkämpfer | BARRACKS | Barracks 2, Smithy 2 | 60 | 30 | 40 | 1 | 40 | 10 | 5 | INF | 18 | 10 | 1300 |
| SCOUT | Scout | Späher | STABLE | Stable 1 | 50 | 50 | 20 | 2 | 0 | 2 | 1 | INF | 9 | 0 | 900 |
| LIGHT_CAVALRY | Light Cavalry | Leichte Kavallerie | STABLE | Stable 3 | 125 | 100 | 250 | 4 | 120 | 30 | 40 | CAV | 10 | 80 | 1800 |
| HEAVY_CAVALRY | Heavy Cavalry | Schwere Kavallerie | STABLE | Stable 10, Smithy 15 | 200 | 150 | 600 | 6 | 150 | 200 | 80 | CAV | 11 | 50 | 3600 |
| RAM | Ram | Rammbock | WORKSHOP | Workshop 1 | 300 | 200 | 200 | 5 | 2 | 20 | 50 | INF | 30 | 0 | 4800 |
| CATAPULT | Catapult | Katapult | WORKSHOP | Workshop 2, Smithy 12 | 320 | 400 | 100 | 8 | 100 | 100 | 50 | INF | 30 | 0 | 7200 |
| NOBLE | Noble | Adliger | ACADEMY | Academy 1 | 40000 | 50000 | 50000 | 100 | 30 | 100 | 50 | INF | 35 | 0 | 10800 |
| PALADIN | Paladin | Paladin | BARRACKS | Barracks 5 | 1000 | 1000 | 1000 | 10 | 120 | 150 | 150 | INF | 14 | 0 | 21600 |
| BANNER_THIEF | Banner Thief | Bannerdieb | BANNER_HALL | Banner Hall 1 | 100 | 120 | 150 | 2 | 0 | 3 | 3 | INF | 18 | 0 | 1500 |

The enum order above is the canonical order (UI columns, report tables, embeddable columns).

The numbers are the unit's values at **level 1**. With the world setting `unitLevelsEnabled` the smithy raises five units (spearman, swordsman, axeman, light and heavy cavalry) to levels 2 and 3 (§5.4). The paladin (M31, §5.5) is recruited in the barracks, limited by slots, and has no levels. The scout has one level (level 2, §5.4). The banner thief (M32b, §21.5) is recruited in the Banner Hall, only while banners are on, and has no levels.

### 5.2 Recruitment

- Recruit time per unit: `max(1, round(baseTime × 0.937^(buildingLevel−1) / speed))`, where `buildingLevel` is the level of the producing building at enqueue time. Reference: spearman at barracks 1 → 1000 s, barracks 25 → 210 s.
- Each producing building (BARRACKS, STABLE, WORKSHOP, ACADEMY, and BANNER_HALL since M32b) has its **own sequential queue**, max `recruitQueueSize` orders.
- A **Drill** banner (M32a, §21.2) shortens `secondsPerUnit` of every **new** recruit order of its village: `max(1, round(secondsPerUnit × (1 − bonus)))`, fixed when the order is queued. Dismissing is not affected.
- An order = (unit type, count ≥ 1). `startAt = max(now, endAt of the last order in that building's queue)`, `endAt = startAt + count × secondsPerUnit`. The time per unit is fixed at enqueue; later levels of the building don't change orders already in the queue.
- **Requirements** (table column "Requires", always including the producing building itself) are checked against the **current** levels: queued building levels don't count (unlike for buildings, §4.3), because the units start training now.
- **Costs (count × unit cost) and population (count × pop) are paid at enqueue.** Check: `floor(stored) ≥ cost` for each resource and `freePop ≥ count × pop` (§5.3).
- Checks in this order, the first one that fails is the answer: queue full, requirements, paladin slots (only for the paladin, §5.5), resources, population. One order has at most 30 000 units (an API limit that keeps the arithmetic in `int`; more than a farm at level 30 feeds).
- **Recruit all** (M27): several orders in one request (at most one per unit type, in any buildings) are queued **all or none**. They are checked and queued one after another in the given order with exactly the checks above, each against what the ones before it left: resources, free population and the free places of its building's queue (two orders for the same building take two places and run one after another). The first order that fails is the answer and nothing is queued.
- Units complete **one by one**, lazily: `finishedBy(t) = clamp(floor((t − startAt) / secondsPerUnit), 0, count)`. `catchUp(t)` moves newly finished units into the village's home stack and stores `completedCount`. Finished orders are deleted. No events are needed for recruitment.
- **Cancel** an order (owner only; an order whose last unit is done no longer exists): the units already finished stay at home; the cost of the units not yet finished is refunded in full, but **received like incoming resources** (§3, capped at the warehouse capacity, the rest is lost – the same rule as for build orders, decided in M5a), and their population is free again. Later orders in that building's queue shift earlier (same rule as buildings: `startAt = min(old startAt, max(now, endAt of the previous remaining order))`); they haven't started yet, because the order in front of them isn't done.

### 5.2a Dismissing units (branch `integration/v1-1-features`)

Rules decided on 2026-10-04 (formerly §15), built after the MVP. Defaults picked for the open points are marked.

- The owner dismisses units of one kind **from the home stack** (units away or in support don't count). They **leave the home stack at once** when the order is placed: they no longer defend and can't be sent.
- **Own queue per producing building** (default, so recruiting is never blocked): the queue of the building that recruits the unit, at most `recruitQueueSize` orders (`QUEUE_FULL`, the same limit as the recruit queue). An order = (unit, count 1 … 30 000); `startAt = max(now, endAt of the last dismiss order of that building)`, `endAt = startAt + count × secondsPerUnit`.
- **Time per unit** = `max(1, round(recruit secondsPerUnit / 5))` at the building's current level, fixed at enqueue. A building that was razed (level 0) counts as level 1 (default).
- Units go **one by one**, lazily like recruiting (§5.2): `finishedBy(t) = clamp(floor((t − startAt) / secondsPerUnit), 0, count)`; the village catch-up frees them and deletes finished orders. No events.
- **Nothing is refunded.** Units still waiting in an order **keep counting for the population** (§5.3) until they are dismissed one by one – only the dismissed ones free their place. Not enough units at home (also: units sent away meanwhile) is `NOT_ENOUGH_UNITS`.
- **Cancel** (owner only): the units not yet dismissed go back into the home stack, the later orders of that building move up (same rule as §5.2). An order that doesn't exist any more is `DISMISS_ORDER_NOT_FOUND`. **Not while an attack is on its way to the village** (`DISMISS_LOCKED`, 2026-10-11, same count as the village's `incomingAttacks`): the units of an order can't be hit, so a cancel then would hide troops. The units keep being dismissed meanwhile; the cancel works again once the attack has landed (or was called off). New orders can still be placed.
- If the village changes owner (conquest), its dismiss orders are dropped without refund (the units are gone either way).

### 5.3 Population

```
popUsed = Σ buildings popTotal(level including queued levels)
        + Σ units owned by the village × pop
          (home stack, stacks supporting elsewhere, units in movements, units in recruit orders not yet finished)
freePop = farmCapacity(current farm level) − popUsed
```
A queued farm upgrade does not raise capacity until it completes. Losing units in battle frees population.
Code: `BuildService.populationUsed` (queued levels) with `UnitService.unitsOf(...).all()` (home, support, units in recruit orders since M5a; units in movements come with M7), used by the build and the recruit checks and by `VillageDto.population.used`.

### 5.4 Unit levels and the smithy (M30, branch `integration/v1-2-features`)

The request (2026-10-13): "units still unlock through the building requirements; a world feature toggle enables unit levels; base level 1 on unlock, the smith can level units up; for now 3 levels with predetermined values. The smithy should show the unit stats: attack, defence, minutes per field." Defaults picked for the open points are marked; the values are placeholders for the playtest (`units.yml`, one place to tune them).

- **World setting** `unitLevelsEnabled` (§1), off by default. Off means exactly the old game: every unit has the table's values (§5.1), nothing can be researched (`UNIT_LEVELS_DISABLED`), the research that exists in the database is ignored (and applies again when the setting is switched back on).
- **Levels belong to the player** (decided 2026-10-13): a research raises the unit in all of the player's villages. A level 1 unit is a unit that is unlocked by its building requirements (§5.1) – nothing changes there.
- **Which units**: spearman, swordsman, axeman, light cavalry and heavy cavalry have levels 2 and 3. The **scout** has level 2 only (the *upgraded scout*, 2026-10-13: it is the unit that sees other units' levels, see *Upgraded scouts* below). Ram, catapult, noble and paladin have none (default; rams and catapults are tuned by §9, nobles by their own rules, the paladin by §5.5).
- **What a level changes**: attack, defence against infantry and defence against cavalry; nothing else (speed, carry, cost, population and recruit time stay, so a level never changes how far a unit walks or what it costs to train). About +10 % (level 2) and +20 % (level 3), whole numbers, and never the same value twice in a row:

| Unit | Att 1 / 2 / 3 | Def inf 1 / 2 / 3 | Def cav 1 / 2 / 3 |
|---|---|---|---|
| SPEARMAN | 10 / 11 / 12 | 15 / 17 / 18 | 40 / 44 / 48 |
| SWORDSMAN | 20 / 22 / 24 | 50 / 55 / 60 | 20 / 22 / 24 |
| AXEMAN | 40 / 44 / 48 | 10 / 11 / 12 | 5 / 6 / 7 |
| LIGHT_CAVALRY | 120 / 132 / 144 | 30 / 33 / 36 | 40 / 44 / 48 |
| HEAVY_CAVALRY | 150 / 165 / 180 | 200 / 220 / 240 | 80 / 88 / 96 |

- **Upgraded scouts (M31 follow-up, 2026-10-13: "you can only see the unit levels starting from level 2")**: the scout's level 2 costs 1000 / 1000 / 400 (20× his recruit cost), takes 12 h at Smithy 1 and needs Smithy 10 like every level 2; his stats move only a little (defence 3 / 2 instead of 2 / 1, attack stays 0), the point is what the report shows. **An attacker's report shows the unit levels of the defenders only if his attack carried scouts at level 2 or more and the scouting succeeded** (the same condition as the other scouted data, §7.1, §14). Level 1 scouts, a failed scouting, a lost battle with no survivor and a world with unit levels off show no levels. His copy has no stacks (he never learns who supports a village), so he gets **one level per unit type: the highest among all defending stacks** (default; the defenders' own copy lists the levels per stack as before). A level 2 scout also does not change the scouting phase itself (§7.1): only the report.
- **Cost and time**: a research costs **20×** (level 2) and **50×** (level 3) the unit's recruit cost, paid when it starts (e.g. spearman level 2: 1000 wood, 600 clay, 200 iron; heavy cavalry level 3: 10 000 / 7 500 / 30 000). It takes `max(1, round(baseSeconds × 0.937^(smithyLevel−1) / speed))` with `baseSeconds` 12 h (level 2) and 30 h (level 3) at Smithy 1 and world speed 1 – the same smithy-level factor as recruiting, with the smithy level of the village at the start. At speed 6 and Smithy 10: about 1 h 7 min for level 2.
- **Requirements** (current levels of the researching village, queued building levels don't count, like recruiting §5.2): **Smithy 10** for level 2, **Smithy 15** for level 3, plus the unit's own requirements of §5.1 (heavy cavalry: Stable 10 and Smithy 15 at every level). Levels go one at a time: level 3 only after level 2.
- **One research at a time per village** (that village's smithy; default) and **one research per unit** at a time in the whole account, so a unit is not researched in two villages at once. `RESEARCH_BUSY` for both. Different units can be researched in different villages at the same time.
- **No events, no catch-up.** A research row holds `startAt`/`endAt`; the level of a unit at time *t* is the highest level of the player's researches with `endAt ≤ t`, 1 if none. A battle reads the levels at the moment it happens (the arrival time of the attack), a research that finishes one second earlier counts. Finished rows stay.
- **Cancel** (owner, while it runs): the full cost is refunded, received like incoming resources (§3, capped at the warehouse capacity, the rest is lost – as for build and recruit orders). An unknown, finished or foreign order is `RESEARCH_ORDER_NOT_FOUND`.
- **Conquest**: a village that changes owner loses its research that is still running, without refund (like dismiss orders, §5.2a). The finished levels belong to the player and stay with them.
- **In battle** (§8, §9): attack and defence use each unit's level of its **owner**. The attacker's levels apply to the attacking units; every defending stack uses the levels of the player who owns it (a support stack keeps its own owner's levels). The report shows the levels above 1 (§14).
- **NPC lords and barbarians have no levels** (default, so the NPC model of 09 stays valid): their units are always level 1, also in a world with levels on. A human attacking them therefore gets the bonus, they do not.
- **Cheats**: the dev and host cheat `finish-orders` also finishes the running research.
- **Smithy page** (`GET /api/villages/{id}/smithy`, web `/v/:id/smithy`): for every unit its attack, defence against infantry and cavalry, **minutes per field in this world** (`minutesPerField ÷ (speed × unitSpeed)`, the walking time of one field), carry, its level and the values of every level, the running research and the next one (cost, time, what is missing, whether the village can pay). With levels off it shows the base values only. Code: `research` package (`ResearchService`, `UnitLevelService`), `rules.ResearchRules`, `rules.UnitLevels`, `gamedata.UnitDef.stats(level)`.

### 5.5 The paladin (M31, branch `integration/v1-2-features`)

The request (2026-10-13): "The paladin, like in tribal wars. A setting in the world 'villages per additional paladin slot'. Basically you start out with the one you have and if you (for example) get to 5 villages you get another slot. Then afterwards after 9 if the setting is 4 up to a maximum that can be determined by a setting again. Default is 5. The paladin makes normal units move at his speed when used as a support. He's faster than spear and axe but slower than cavalry." Defaults picked for the open points are marked; the unit's numbers are placeholders for the playtest (`units.yml`, `buildings.yml`).

- **Where:** in the **barracks** (2026-10-13: "Paladin in the barracks with a visible limit counter as a subtext"), the last unit of its list, with its own order in the barracks queue like every unit. He needs **Barracks 5** (default). There is no separate building.
- **Paladin** (§5.1): 1000 / 1000 / 1000, 10 population, 6 h to train at Barracks 1 (`21600 s`, the usual speed formula and the barracks factor), attack 120 (an infantry-type attacker), defence 150 against infantry and cavalry, carries nothing, **14 minutes per field**. Deliberately a strong defender and a weak raider: he is a general who walks with the army.
- **Slots.** A player has `min(paladinMaxSlots, 1 + (villages − 1) ÷ paladinVillagesPerSlot)` slots (whole division), where `villages` is **every village he owns right now**, conquered ones included (barbarian villages only count once they are his). With the defaults 4 / 5: 1 slot with 1–4 villages, 2 with 5–8, 3 with 9–12, 4 with 13–16, 5 with 17 or more. `paladinMaxSlots = 0` means no paladin in this world: the barracks does not list him, a Town Hall upgrade does not announce him, and recruiting him is refused (`PALADIN_DISABLED`).
- **What takes a slot (default):** every paladin the player owns, wherever he is: at home, in support elsewhere, on the way (movements), waiting in a recruit order (finished or not, the whole order counts from the moment it is placed) and waiting in a dismiss order (he counts until he is dismissed, like population, §5.2a). **Recruiting beyond the free slots is refused** (`PALADIN_SLOTS_FULL`; also all-or-nothing for "Recruit all", §5.2); a refused order costs nothing. The check and the order are done under a lock on the player, so two villages cannot fill the same slot at the same time.
- **When a slot disappears (default: nobody loses a paladin).** If villages are lost (conquest, §10), the player may have more paladins than slots. They stay alive and keep fighting; he just cannot recruit another until the slots catch up again (more villages, or paladins fall in battle or are dismissed). Taking a paladin away when a village falls would hit the loser twice.
- **When his village is conquered.** A paladin belongs to a home village like every unit (§6): the ones standing **outside** the conquered village are lost with it, as all units of that village are (§10), and that frees their slots at once. Paladins in a village that holds out are in the battle like any defender; if the battle is lost they are dead.
- **Support speed.** A SUPPORT (§6) that contains a paladin walks at **the paladin's pace, not the slowest unit's**: the whole stack walks 14 minutes per field even if it also holds swordsmen (22) or rams (30). That is his one use in support. It also applies when the support is sent back (RETURN of a support stack) and to the preview. An ATTACK keeps the slowest-unit rule, so a paladin never speeds up an attack; sent with slow rams he is slow as well.
- **Battle (§8, §9).** The paladin is a normal unit with the table's values; he defends at home and in support and attacks, and dies like the others. **No levels** (M30, §5.4): the smithy lists him with his values and no research (`MAX_LEVEL_REACHED`), a level in a simulator request is ignored. The population-based loot rule is untouched, he carries nothing.
- **NPC lords and barbarians have no paladins** (default): the templates never train him (the validation rejects a paladin in a mix), so the NPC model of 09 stays valid. A barbarian village never has one.
- **Farm helper (§18):** a template cannot contain the paladin (`400`), like the noble.
- **Dismissing** a paladin is allowed (§5.2a) and frees his slot when the order reaches him.
- **Admin:** the two settings of §1 sit with the other world settings: the card on the admin page, `bannerfall-admin world set paladin-villages-per-slot|paladin-max-slots <n>` and the world info. A running world may change them: raising the maximum opens slots at once, lowering it follows the rule "nobody loses a paladin".
- **Pages:** under the paladin's name in the barracks list a **slot counter**: a dot per slot (filled = taken), "1 of 2 slots in use" and "Next slot at 9 villages (you own 6)" or "This is the most slots the world allows"; the free slots limit the amount for the paladin everywhere (the "max" button, "Recruit all"). The rally point's confirm screen says that a support with a paladin walks at his pace. API: `GET /api/paladin` (04-api).

---

## 6. Movements (commands)

Types: `ATTACK`, `SUPPORT`, `RETURN`.

- **Army speed** = the slowest unit in the command (largest minutes per field). **Exception (M31, §5.5):** a SUPPORT that contains a paladin walks at the paladin's pace. This goes for the way there, the preview and a support sent home again.
- **Travel time** (seconds): `max(1, round(distance × minutesPerField × 60 / (speed × unitSpeed)))`. A **Haste** banner of the source village (M32b, §21.2) shortens ATTACKs and SUPPORTs: `max(1, round(time × (1 − haste)))`, snapshotted when the command leaves; the RETURN after a battle keeps it, a withdrawn or sent-back support walks home without it (§21.16).
- Sending removes units from the home stack immediately (after `catchUp(now)`).
- A command must contain at least 1 unit; you can't send units you don't have at home.
- **ATTACK** targets: any village not owned by the sender. Not allowed if the target player's beginner protection lasts beyond the arrival time (`PROTECTED_TARGET`). **Sending an attack on a player's village (an NPC lord's included) ends your own beginner protection; an attack on a barbarian village does not, unless it carries nobles** (M25, 2026-10-13: protected players may farm barbarians). Decided when the attack is sent, by who owns the target then. Nobles end it even against a barbarian village (M25 review, 2026-10-13): a conquest would otherwise give the village to a protected player while attacks sent at it as a barbarian village are on the way (`PROTECTED_TARGET` is checked at send time only), and protected players could expand without giving up the protection.
- **SUPPORT** targets: any village other than the source (own villages included).
- **Nobles:** distance must be ≤ `nobleMaxDistance`. Only allowed in ATTACK.
- **Catapult target:** chosen when sending an attack that contains catapults; default `HEADQUARTERS`.
- **Cancel:** the owner can cancel ATTACK or SUPPORT within `cancelWindowSeconds` after departure. The command becomes `RETURN` with `arriveAt = now + (now − departAt)`. Exactly: while `now < min(departAt + cancelWindowSeconds, arriveAt)` (a command that has arrived, or arrives this very moment, can't be cancelled; `CANCEL_WINDOW_OVER`). The RETURN departs at `now`, its source is the old target, its catapult target is dropped.
- **Arrival:**
  - ATTACK → battle (§7–§10). Survivors start a RETURN with their loot.
  - SUPPORT → units join (or create) the stack `(home = source, location = target)`. Reports go to both owners ("support arrived").
  - RETURN → units join the home stack, loot is added with the incoming-resources rule (§3).
- **Withdraw support:** the owner of the home village sends a stack (or part of it) home → RETURN. **Send back:** the owner of the location village can do the same for supports in his village. The owner of the other village gets a "support returned" report at once (none if both villages are his; M8b, §14).
- If the home village changes owner (conquest, §10), every unit belonging to it outside the village is **lost**: its stacks elsewhere are deleted and its movements are cancelled without return (events deleted).
- **On the map (M26, 2026-10-13):** the target village of a movement gets a marker – red crossed swords for attacks, yellow crossed swords for attacks that carry nobles, a green sword for supports. A player only sees markers for **his own commands** (attacks and supports sent from his villages, wherever they go) and for **what is on the way to his own villages** (foreign attacks and supports) – the same he sees at his rally points, so the map reveals nothing about other players' movements (like M21's loyalty rule). Yellow only for his own attacks: a foreign attack hides its units, so a defender sees an incoming noble attack in red. His own attack on a village he owns by now gets no marker (it arrives like a support, §10).
- **Returning troops on the map (M36, 2026-10-14):** when his own troops walk home (survivors of an attack, a cancelled command, a support withdrawn or sent back), the village they return to gets a blue badge with the rally point's "returning" arrow in the **top right** corner of its field, with the number of returns from 2 on. Only his own returns, and only on his own villages (a RETURN always walks to the units' home): the defender's map doesn't follow an attacker home, and nobody sees another player's troops returning.


### 6.1 Attack trains (M37, branch `integration/v1-2-features`)

The request (2026-10-14): "The ability to launch multiple attacks on the same village, similar to tribal wars." Any number of attacks could always be sent at one village, one by one, each through its own confirmation and at its own pace. A **train** sends several attacks from one village at one target with one confirmation, so that they land one right after the other (the Tribal Wars noble train: a clearing attack, then the nobles).

- **Up to 5 attacks** per train (`TrainRequest.MAX_ATTACKS`), each with its own units and its own catapult target. Attacks only: a support is never part of a train.
- **Pace (decided 2026-10-14: "Train pace"):** every attack of the train walks at the pace of the **slowest unit in the whole train** (a light cavalry attack in a train with nobles walks at the nobles' 35 minutes per field). All of them depart now and **arrive at the same moment**; Haste, War and Plunder of the source village are snapshotted for each of them as for a single attack (§21.2).
- **Order:** they arrive in the order they were lined up. They share their `arriveAt`; the event worker runs events of the same time in id order (`due_at, id`), and the train's movements and events are created in that order. Each attack is a full battle of its own (§7–§10) that sees what the ones before it left: dead defenders, a lowered wall, lowered loyalty. Once an earlier noble has conquered the village, the rest arrive like a support (§10).
- **Checks:** every attack is checked like a single one (§6: units, own village, nobles, beginner protection with the train's arrival), its units against what the attacks before it leave at home (a refused attack takes nothing, so the later ones are judged without it). **All or none:** the train is sent only if no attack breaks a rule; otherwise nothing leaves (`409` with the first error of the first attack that breaks one).
- **After sending** each attack is an ordinary ATTACK: listed on its own, cancelled on its own (§6 cancel), its survivors walk home on their own. Beginner protection ends as for a single attack (a target with a player, or nobles in any attack of the train). The target's owner gets **one** "incoming attack" notification for the whole train; his incoming list and counter show every attack.
- NPC lords keep sending their clearing attack and nobles as separate commands (09); the farm helper sends single attacks.

---

## 7. Battle overview

Executed at the attack's `arriveAt`, inside one transaction, in this order:

1. Lock target and source villages (lock order by id), `catchUp(arriveAt)` both.
2. Scouting phase (§7.1).
3. If the attacker has non-scout units: main battle (§8).
4. If the attacker won: wall damage, catapult damage (§9), loot (§9.3), loyalty and conquest (§10).
5. Apply losses to every involved stack, create the RETURN command for survivors (if any), write reports, recompute points.

Code (M8b): `combat.BattleService.resolve(movement, at)`, called by `MovementArriveHandler` (which locks and catches up both villages, step 1). Defending stacks are passed to `Combat.fight` with the village's own units first, then the supports by id; a support stack that loses all its units is deleted. Points change only when the wall or the catapult target lost levels (points come from buildings). The survivors walk home at the speed of the slowest survivor. Loyalty and conquest (§10) have a marked hook for M11 after the loot.

**Randomness used:** luck (one value per battle), loyalty drop per noble. Both come from `RandomSource`.

### 7.1 Scouting phase

Only if the attack contains scouts. `Sa` = attacking scouts, `Sd` = all scouts in the target village (all stacks).
- If `Sa > Sd`: attacker loses `round(Sa × (Sd / Sa)^1.5)` scouts (0 if `Sd = 0`). **Scouting succeeded.**
- Else: all attacking scouts die. Scouting failed.
- Defending scouts never die in the scouting phase.
- Surviving attacking scouts then stay with the army (attack 0). If there is a main battle they share the attacker's loss ratio.
- An attack with **only scouts** has no main battle: survivors return and the report shows what they saw.

Scouting success reveals: resources (before loot), building levels (before the battle), and the sum of all units in the village (own and support, per unit type – not whose they are) – in the report only if an attacking unit survived (§14).

## 8. Main battle

```
Ainf = Σ count × att   over attacking units of type INF
Acav = Σ count × att   over attacking units of type CAV
if Ainf + Acav == 0 -> no main battle (scouts and/or banner thieves only, handled above; §21.5)

luck  ∈ [−luckRange, +luckRange] uniform
A     = (Ainf + Acav) × (1 + luck) × (1 + war)              // war: the attack's War banner snapshot (M32b, §21.2), else 0

shareInf = Ainf / (Ainf + Acav);  shareCav = 1 − shareInf
Wb    = wallAfterRams(wallLevel, floor(ramsSent / 2))          // effective wall during the battle (§9.1)
Dunits = Σ over every defending unit (all stacks in the village, all types incl. scouts and nobles)
            count × (defInf × shareInf + defCav × shareCav)
D     = (Dunits + 20 + 50 × Wb) × (1 + 0.05 × Wb) × (1 + bulwark)   // bulwark: the village's Bulwark banner at battle time, else 0

if A > D:  attacker wins.   attackerLossRatio = (D / A)^1.5,  defenderLossRatio = 1
else:      defender wins.   defenderLossRatio = (A / D)^1.5,  attackerLossRatio = 1
losses per type per stack = round(count × lossRatio)
```
Notes:
- **Unit levels (M30, §5.4):** `att`, `defInf` and `defCav` are the values of the unit's level for its owner (each defending stack uses its owner's levels); without `unitLevelsEnabled`, and for NPC lords and barbarians, they are the table's. Nothing else of the formula changes.
- The base defence `20 + 50 × Wb` means even an empty village needs a few units to take.
- Each defending stack (owner's own and every support) loses with the same ratio, rounded per stack.
- A tie (`A == D`) goes to the defender.
- Code (M8a): `rules.Combat.fight(GameData, BattleInput) → BattleResult` does §7.1, §8 and §9.1–§9.2 in one pure call (loot is §9.3, `rules.Loot`, done by the caller because it needs the target's stock). Luck is drawn by the caller with `Combat.drawLuck(random, luckRange)`: one `RandomSource` draw, uniform in `[−luckRange, +luckRange)`; `luckRange` must be in [0, 1].

**Test vectors** (luck = 0):

| Case | A | D | Result |
|---|---|---|---|
| 100 axemen vs 100 spearmen, wall 5 | 4000 | (1500+20+250)×1.25 = 2212.5 | attacker wins, ratio 0.41137 → loses 41 axemen; all spearmen die |
| 50 light cav vs 100 spearmen, wall 0 | 6000 | 4000+20 = 4020 | attacker wins, ratio 0.54842 → loses 27 light cav |
| 20 axemen vs 100 swordsmen, wall 0 | 800 | 5000+20 = 5020 | defender wins, ratio 0.06362 → loses 6 swordsmen; all axemen die |
| 10 scouts vs 4 scouts | – | – | scouting succeeds, attacker loses round(10×0.4^1.5)=3 |

## 9. Siege and loot (attacker won only)

Code (M8a): `rules.Siege` (§9.1, §9.2; `Combat.fight` applies both and returns `wallAfter` / `catapultTargetLevelAfter`), `rules.Loot` (`capacity`, `available`, `distribute`).

### 9.1 Rams → wall

```
ramsNeeded(L) = 2 + L
wallAfterRams(level, rams):
    while level > 0 and rams >= ramsNeeded(level):
        rams -= ramsNeeded(level); level -= 1
    return level
```
- During the battle: `Wb = wallAfterRams(wallLevel, floor(ramsSent / 2))` (temporary).
- After a won battle: `newWall = wallAfterRams(wallLevel, survivingRams)` (permanent).
- Reference: wall 20 with 50 rams → 18; wall 20 needs 250 rams to reach 0; wall 5 with 10 rams → 4.

### 9.2 Catapults → target building

```
catsNeeded(L) = 4 + 2 × L
levelAfterCatapults(level, cats, minLevel):
    while level > minLevel and cats >= catsNeeded(level):
        cats -= catsNeeded(level); level -= 1
    return level
```
- Uses **surviving** catapults, applied after the ram damage (so targeting WALL stacks with rams).
- `minLevel` = 1 for HEADQUARTERS, FARM, WAREHOUSE, RALLY_POINT; 0 otherwise.
- Reference: HQ 20 with 100 catapults → 18.
- If the target building's level drops below a level in a queued build order, that order stays valid (it upgrades from the new level, target level is recomputed when it completes: `level = level + 1`). In code (M8b) the queued orders of a building that lost `n` levels get their target level lowered by `n` right away (`BuildService.levelsLost`), so the queue shows the right levels and a later cancel or move – which checks that each order is the next level of its building – still works. Costs and times stay as paid. The same applies to the wall after rams.

### 9.3 Loot

```
capacity = Σ surviving attacking units × carry      (× (1 + plunder), rounded, with a Plunder banner, M32b §21.2)
avail[r] = max(0, floor(stored[r]) − hidingCapacity(hidingLevel))   for r in wood, clay, iron
loot = [0,0,0]; remaining = capacity
loop:
    open = resources with avail[r] − loot[r] > 0
    if remaining == 0 or open is empty: break
    share = max(1, floor(remaining / |open|))
    for r in open (order: wood, clay, iron):
        take = min(share, avail[r] − loot[r], remaining)
        loot[r] += take; remaining −= take
stored[r] −= loot[r]
```
Example: capacity 1000, stored 300/2000/2000, hiding 150 → avail 150/1850/1850 → loot 150/425/425.

## 10. Loyalty and conquest

- Every village has `loyalty` (0–100, starts at 100). Lazy regen: `min(100, loyalty + loyaltyRegenPerHour × speed × hours)`.
- If the attacker won and **surviving nobles ≥ 1** and the target is not owned by the attacker: for each surviving noble, `loyalty −= randomInt(20, 35)` (inclusive). An **Oath** banner of the attacked village (M32b, §21.2) makes each hit `round(randomInt(20, 35) × (1 − oath))`.
- If `loyalty ≤ 0` → **conquest**:
  1. Owner := attacking player. `loyalty := 25`. A barbarian village has no name and gets one now (default: like a new village, §2) – the API never sends a player's village without a name.
  2. One noble is consumed. All other surviving attackers stay in the conquered village as a support stack `(home = source village, location = conquered village)`. No RETURN command is created.
  3. Every unit belonging to the conquered village outside it is lost (§6). Its build and recruit orders are deleted **without refund**, their events deleted. Its open market offers are deleted (reserved resources lost). Incoming transports still arrive and go to the new owner. Its own outgoing transports still deliver, but their merchants were the old owner's: they don't come back, so they neither count as busy for the new owner nor show in his market (code review 1.2).
  4. Reports: attacker "conquered", previous owner "village lost". Both name the banner that flew in the village, if any, and whether it went to the conqueror's stash (§21.6.1, M32b).
  5. Points of both players recomputed.
- Barbarian villages can be conquered the same way.
- A player with **0 villages** gets `needsRespawn = true` and can create a new starting village at a fresh spawn spot (§2).
- An NPC lord with 0 villages is out of the game for good (M12a, §11b): no respawn, no more decision rounds.

**Decisions in M11 (2026-10-07):**
- An attack that arrives at a village its sender owns by then (a later noble wave after the conquest, or any attack sent before) **arrives like a support**: no battle, the units join the sender's stack there, a `SUPPORT_ARRIVED` report. An own attack also no longer counts as an incoming attack (`incomingAttacks`, red counter).
- Order of the reports of a conquering battle: `ATTACK`/`DEFENSE` (with loyalty before → after and "conquered") first, then `CONQUEST` to the conqueror and `VILLAGE_LOST` to the previous owner (none for a barbarian village). Both show the village as it was before (name, owner) and the name it has now.
- A conquering battle takes **no loot** (review, 2026-10-07): the army stays in the village, so its resources stay there and belong to the conqueror with it (before the fix the loot was taken and then vanished with the RETURN that never happened). The battle reports show loot 0.
- Supports still on their way to the village when it falls **arrive** and stand in the conqueror's village; their owners can withdraw them (M11 review, 2026-10-07).
- Foreign supports in the conquered village stay (as for M10's conversion to a barbarian village); their owners can withdraw them. Their supporters get the `DEFENSE` report as usual.
- A respawn gives fresh beginner protection (§13, `beginnerProtectionHours` from now) and spawns **no** barbarians around the new village (it would let a player farm fresh barbarians by losing his last village). It is refused (`409 RESPAWN_NOT_ALLOWED`) while the player still owns a village, and for NPC lords.
- Market offers: `OwnerChange.dropOrders` deletes the village's open offers (M13a); transports already on the way still arrive and go to the new owner. The village's outgoing transports end their way home at the conquest (`TradeTransport.lostWithVillage`): the goods arrive, the merchants are free.

**Who sees a village's loyalty (M21, 2026-10-12).** Taking a village over without any information sharing must not be a valid strategy, so the loyalty of a village is **not public**:
- Your own villages show it live (with the regeneration up to now).
- Any other village (players', NPC lords', barbarians') shows only **the value you saw when you last attacked it**, with the time you saw it. It is not extrapolated: after a few hours it is stale, which is the point. A village you never attacked shows "unknown".
- You see it when an attack of yours on the village had at least one unit that came back (the same rule as for the rest of the report, §14). The value is the loyalty **after** the battle: with surviving nobles the new value (the report says before → after, as in M11), without nobles the unchanged one. A scouting-only attack counts. A battle in which nobody comes back, support, trade and a village that fell to you tell you nothing (a village you conquered is yours and shows live).
- The sighting belongs to the player. **Guild members don't share it** (default; no rule for it yet). Deleting the report doesn't delete the sighting.
- On a world that already ran: the sightings start from the attack reports with surviving nobles that exist at the deploy (migration V23); nothing else is back-filled.
- NPC lords are not restricted: their decision code reads the true loyalty (they play from the database, §11b), so this rule is for humans.

**Code (M11):** pure `rules.Loyalty` (`afterNobles`, `conquers`, `AFTER_CONQUEST = 25`); `BattleService.resolve` lowers the loyalty after the wall and catapult damage, takes loot only when the village didn't fall, and calls `ConquestService.conquer` after writing the battle reports; the steps that M10's conversion and the conquest share (deleting orders, losing units elsewhere and movements) are `village.OwnerChange`.

## 11. Barbarian villages

- `player = null`. Display name comes from the client ("Barbarian village" / "Barbarendorf").
- At spawn: WOODCUTTER/CLAY_PIT/IRON_MINE random 1..5 each, WAREHOUSE 5, FARM 1, HQ 1, WALL random 0..2 (**0 with `barbarianWallsEnabled`**, M29, see *Walls* below), everything else 0. Resources 50 % of capacity. No units. Loyalty 100.
- Barbarians produce resources like any village (lazy). They never build or recruit.
- **Growth** (if enabled): a recurring event every `24 h / speed` (whole seconds, rounded) increases one random mine level by 1 in every barbarian village (max 12) and recomputes points. The mine is drawn among the mines still below 12 (one draw); a village with all three at 12 doesn't grow. The village is caught up with the old levels first. The next round is due `24 h / speed` after the previous one's due time (not after the moment it was processed), so a server that was down catches up round by round. With growth switched off the event still runs and only schedules the next round, so switching it on needs no restart.
- **Walls** (M29, 2026-10-13: a wall on the barbarians from the first day makes farming before the rams are there inefficient). The host's settings `barbarianWallsEnabled`, `barbarianWallStartDays`, `barbarianWallMaxLevel`, `barbarianWallsRebuild` (§1); the defaults leave a world as it was. With `barbarianWallsEnabled` on:
  - a new barbarian village starts with **wall 0** (the wall die is still rolled, so the other dice stay where they were);
  - the growth round (above) whose due time is at least `barbarianWallStartDays` real days after the world opened (`world_settings.initialized_at`, the moment the pre-game ended; not scaled by `speed`) adds **one wall level** to every barbarian village whose wall is below `barbarianWallMaxLevel`, in the same round and the same catch-up as the mine level. Walls grow whether `barbarianGrowthEnabled` is on or not;
  - the cap only limits what barbarians build, it never lowers a wall. A village that turned barbarian from a banned or deleted player (§11, last bullet) keeps a wall above the cap and does not grow it; one below the cap grows like any other. This is the reading of "not including disabled players" in the request (the cap does not apply to them);
  - a wall that **rams knocked down** (§9: a won battle on a barbarian village lowers its wall level) is marked `village.wall_razed`. Without `barbarianWallsRebuild` it never grows again, so farmers who clear it get a village that stays open; with it, it grows back one level per round up to the cap. The mark is cleared when a village turns barbarian again (walls rams knocked down before V24 have no mark, so in the playtest realm they grow back once walls are switched on);
  - switching it off stops the building; walls already built stay. Switching it on in a running world does not touch existing walls: they grow from the next round on (when the start day has passed), and the old random walls 0–2 are simply the starting point.
- The initial barbarians are spawned at the first start of the server (`world_settings.initialized_at`, once per world); the growth event is scheduled one interval after that start (and again at any start that finds none pending). The barbarians around a new player are spawned in the registration's transaction; if the area has no room, fewer are spawned and the registration still succeeds (the same for the initial ones).
- When a player is deleted by the admin (M14a), his account is disabled (no login, no respawn; the name stays taken) and his villages become barbarian villages: their units are deleted (at home, supports elsewhere, everything on the way, with their events), build and recruit orders are deleted with their events (no refund), market offers deleted (M13), name and owner cleared. Supports of other players standing in them stay (their owners can withdraw them). The player row stays (reports point at it) with 0 villages and 0 points.

## 11b. NPC lords

Computer opponents that feel like players ("Drivatars"): a name, villages, points in the ranking, reports with their name in them. They make the world worth playing with only two humans. Final since **M12a** (2026-10-07). This section holds what a human player can notice; how a lord thinks (decision round, economy, targeting, memory), the templates and all numbers are in the NPC lord design notes (09) and the code under `server/src/main/java/de/bannerfall/npc/`.

**Identity and life**
- An NPC lord is a `player` with `npc = true` and no account. Name from a fixed list (EN/DE-neutral, e.g. "Lord Aldric", "Gräfin Hedda"; 09 §2). **Every NPC name contains a space**, which usernames can't (`[A-Za-z0-9_-]`): player names are unique, so an NPC name can never block a registration.
- Spawned like a human (§2, with `barbariansPerNewPlayer` barbarian villages around it) at world creation: `npcLordCount` lords, personalities in the order BALANCED, OFFENSIVE, DEFENSIVE, BALANCED, … **No more appear when humans join**; the admin can spawn more (M12d).
- **No beginner protection** (it would end with its first attack on a player anyway, §6).
- Shown with a small marker on the map, in the ranking and on the player page ("NPC lord"). **Its personality is not shown** to players (only to the admin): finding out who is aggressive is part of the game.
- **Out of the game for good** when it loses its last village: no respawn (`409 RESPAWN_NOT_ALLOWED`), no more decision rounds; it stays in the ranking with 0 villages.

**Personalities**

| Personality | Units | Attacks and conquers | Defends |
|---|---|---|---|
| DEFENSIVE | spearmen, swordsmen, heavy cavalry; early wall | farms barbarian villages; raids only undefended villages below 50 % of its attacking village's points; conquers barbarian villages only; never retaliates, arms itself instead | sends half of its defensive units at home to an own village under attack |
| OFFENSIVE | axemen, light cavalry, rams; little wall | anything it estimates it can beat, humans included; raids undefended player villages for loot; retaliates against whoever attacked it; conquers whenever it has nobles | rarely: what stands in the village |
| BALANCED | mixed | farms barbarian villages; raids and conquers clearly weaker players (village < 80 % of its attacking village's points, and a human only if below 75 % of the lord's player points) and anyone who attacked it in the last 48 h ÷ speed | sends a quarter of its defensive units |

NPC lords fight each other by the same rules.

**Playing by the rules**
- NPC lords act **through the same services and rules as humans** (build, recruit, command). No hidden resources, no instant units.
- They know only what a human could know: the map (owners, points; their code reads the true loyalty, M21 does not apply to them), the ranking, their own villages and movements, the incoming movements a human would see (no units of foreign attacks), and **their own reports** – what they scouted or saw in battles. They read their reports each round (09 §4); nobody else does, so reports of NPC lords are deleted 7 days after they were created.
- Before an attack on a player village they scout it and estimate the battle with the real formula (§8, luck 0) against what they saw, with a safety margin (09 §6.1); they attack only if the estimate is a clear win.
- They decide every `npcThinkMinutes ÷ speed` (20 min at speed 1) with ±25 % jitter, so they react more slowly than a human who's online.

**Balance limits** (scaled by `npcDifficulty`; numbers in 09 §8)
- **Production factor:** every village of an NPC lord produces × its factor (§3): EASY 0.7, NORMAL 0.9, HARD 1.1. Humans and barbarian villages: 1.0.
- **Rubber band:** a lord far ahead of the strongest human (NORMAL: above 125 % of his points) sends no nobles until the humans catch up; a lord far behind the humans' average (NORMAL: below 50 %) produces 25 % more.
- **Attack caps** per real 24 h (scouts included, a noble train – clearing attack and nobles sent together – counts once, barbarian villages not counted): one lord against one player at most 2 / 4 / 8 commands (EASY / NORMAL / HARD); all lords together against one human at most 3 / 8 / 16.
- **Never** against players under beginner protection.
- **Quiet hours** (EASY and NORMAL): no NPC command arrives at a human village between 23:00 and 07:00 Europe/Berlin.
- **A human's last village is spared** (EASY and NORMAL): NPC lords may farm and attack it but never send nobles to it.
- **Build quiet hours:** NPC lords queue no new buildings 22:00–08:00 (EASY), 23:00–07:00 (NORMAL), 00:00–06:00 (HARD), Europe/Berlin; what is queued keeps building.
- **CUSTOM:** a difficulty whose values the host sets in the admin page (a copy of NORMAL to start with).

**Testing balance:** a headless simulation (M12d, 09 §11) runs a world with only NPC lords (and once with two sleeping humans) for 50 days at speed 3 and prints points, villages and battles per personality. It's used to tune the templates before friends play.

**Later (not MVP):** Drivatar-style learning – NPC lords copy build orders and unit mixes that human players used successfully; NPC lords trading on the market; chat lines from NPC lords.

## 12. Market

| Quantity | Formula |
|---|---|
| Merchants at market level L | `L ≤ 10 ? L : 10 + (L − 10)²` (L 25 → 235) |
| Capacity per merchant | 1000 resources (sum of all three) |
| Merchant speed | 6 minutes per field (travel formula of §6) |
| Merchants needed for an amount | `ceil(total / 1000)` |

- **Busy merchants** = merchants of outgoing transports with `returnAt > now` + merchants reserved by the village's open offers. Free = total − busy. Computed by query, no events.
- **Send resources:** to any other village. Resources are deducted immediately. Arrival (event `TRADE_ARRIVE`) adds them with the incoming-resources rule. `returnAt = arriveAt + travel time`.
- **Offers:** "give `offerAmount` of R1 for `wantAmount` of R2, `count` times" (R1 ≠ R2, `1/tradeMaxRatio ≤ want/offer ≤ tradeMaxRatio`). Creating an offer deducts `count × offerAmount` of R1 and reserves `count × ceil(offerAmount/1000)` merchants.
- **Accept** `k` times (1 ≤ k ≤ remaining count) by a village of another player: the acceptor pays `k × wantAmount` of R2 immediately and needs `ceil(k × wantAmount / 1000)` free merchants. Two transports start: offer village → acceptor (`k × offerAmount` of R1, uses the reserved merchants) and acceptor → offer village (`k × wantAmount` of R2). Remaining count −= k; at 0 the offer is deleted.
- **Cancel offer:** refund the remaining `count × offerAmount`, release merchants.
- You cannot accept offers of your own villages.

**Decisions in M13a (2026-10-07):**
- Merchants are counted per unit of an offer: `count × ceil(offerAmount / 1000)`, so an offer of 3 × 1500 wood reserves 6 merchants, not 5. The same number is what accepting `k` units sends from the offer's village.
- Order of the checks: the village is yours, the target exists, the request is not empty or to the same village, then merchants, then resources (`NOT_ENOUGH_MERCHANTS` before `NOT_ENOUGH_RESOURCES`). An offer needs `offerResource ≠ wantResource` (`SAME_RESOURCE`), a transport at least one resource (`EMPTY_TRANSPORT`) and a target other than the sender (`SAME_VILLAGE`). Amounts are at most 1 000 000 per resource and an offer at most 1000 units (validation).
- Free merchants never go below 0: a market that was hit by catapults while merchants are away shows 0 free, not a negative number. The merchants of an accepted offer's side are the ones it reserved at creation, so accepting needs free merchants only for the acceptor's payment.
- Resources can be sent to a barbarian village (they are received there, nobody hears of it). Merchants of an accepted offer walk to the acceptor and back with the same travel time formula as plain transports; a transport that arrives gives the owner of the target village a `TRADE_ARRIVED` report ("Merchants from A arrived in B", what was delivered) and a `VILLAGE_UPDATED` push. The sender gets no report. The owner of an offer hears of an acceptance through `VILLAGE_UPDATED` (the trade report comes when the goods arrive).
- Cancelling an offer refunds what is left, capped at the warehouse like every refund (§4.3). Accepting the last unit deletes the offer.
- NPC lords do not trade in the MVP (09 §1).

**Decisions in M13b (2026-10-07):** the `TRADE_ARRIVED` report says what the warehouse could not take (`Village.receiveResources` returns the lost amount, counted in whole units like the shown stock); the market page checks the same limits as the server before it enables a button (free merchants, stock, ratio – `tradeMaxRatio` comes with `GET /api/world`) and the server still decides. The dev cheat `finish-movements` lets the merchants a village sent arrive too.

## 13. Chat

- One global channel. Since M15b each guild has a channel of its own (§16.1).
- Message: 1–500 characters after trimming. Max 1 message per second per player (`CHAT_RATE_LIMIT`).
- History: last 100 messages on load, new ones pushed live.
- No editing; the admin can delete messages.

## 14. Reports

One report row per recipient. Kinds: `ATTACK` (attacker view), `DEFENSE` (defender and supporters), `SCOUT` (scouts-only attack), `SUPPORT_ARRIVED`, `SUPPORT_RETURNED`, `TRADE_ARRIVED`, `CONQUEST`, `VILLAGE_LOST`.

Visibility rules:
- The attacker sees the defending units only if at least one attacking unit survived.
- The defender always sees the attacking units and their losses.
- Every player whose support stack fought gets a `DEFENSE` report.
- Scouting results appear only if scouting succeeded.

Reports have a read flag; players can delete their own reports, one at a time or **all at once** (M38: "Delete all" after one confirmation removes every report up to the newest one the list showed, read or unread; a report that arrives while the player confirms stays).

**Sending a report to other players (M40, v1.2.1).** A player sends any of his own reports to one or more players by name (at most 10 per send, names ignore case, a name given twice counts once):
- Each recipient gets a **copy in his own reports list**: the same kind, time and payload, unread, marked "Sent by Anna". In the list a copy sorts by when it **arrived**, so it lands on top like new mail; its line and the report still show when it happened (M40 review). The copy shows the sender's view unchanged ("you" and "your" mean the player whose report it was, which the copy names), so nothing in it is recomputed for the recipient. A copy can be sent on; it keeps naming the player whose report it was first, and says who sent it last. Copies are independent rows: deleting the original leaves them, and a copy is read, deleted and counted like any other report. The recipient is notified (`REPORT_CREATED` with the sender's name).
- **The rules of private messages (§17) apply:** human players only (an unknown name, an NPC lord or a deleted account is `PLAYER_NOT_FOUND`), not to yourself (`OWN_PLAYER`), not to a player you blocked (`PLAYER_BLOCKED`), **one send per second and sender, shared with private messages** (`MESSAGE_RATE_LIMIT`), and at most **30 unread copies from one sender** in one recipient's list (`INBOX_FULL`). A send is **all or nothing**: the first name that fails stops it, nothing is sent, and the error names that player.
- **A recipient who blocked the sender** is told nothing and the sender can't tell: the send succeeds, the copy is stored as read and without a notification, and the limit of 30 counts the copies since the block (as for messages). While the block lasts the recipient's list and unread count leave out every copy from that player, the earlier ones too; unblocking brings them back. ("Delete all" still deletes them, since it deletes everything up to the newest report.)
- A report whose stored payload can't be read any more can't be sent (`REPORT_UNREADABLE`).
- Defaults picked (overrulable): no note with the report (the private messages are for that); a copy keeps the original's kind and result colour, seen from its first owner's side; the "Add to farm list" link works on copies (the reader's current village, without the sender's units as template).

Details (M8b):
- **Who gets what:** the attacker gets `ATTACK`, or `SCOUT` for a scouts-only attack. The owner of the attacked village gets `DEFENSE` (a barbarian village: nobody). Supporters get the same `DEFENSE` report as the owner, but only if there was a main battle – against scouts only nothing happens to their units, so they aren't bothered. A player gets each kind once per battle, even with several stacks in the village (an attacker whose own support stands in the target gets `ATTACK` and `DEFENSE`). NPC lords get reports like everyone and read them (M12a, §11b); theirs are deleted 7 days after creation.
- **"At least one attacking unit survived"** decides everything the attacker learns about the other side: the defending units and losses per unit type, the wall before/after and the catapult damage. He never learns which of those units belong to a supporter: the per-stack list (whose units stood there) is only in the defenders' copy (M8b review). Scouted data (resources before the loot, building levels before the battle) is shown only if, in addition, scouting succeeded – scouts that die in a lost main battle bring nothing home. The attacker always sees his own units and losses, the luck (none for scouts only), his catapult target and the loot.
- **The defenders** see everything about the attackers and whether their scouting succeeded, but not the scouted data (it's their own village).
- Each recipient's copy is stored already cut down to what he may see.
- **Unit levels (M30, §5.4):** a battle report stores the research levels above 1 of the attacking unit types (`attackerLevels`) and of every defending stack (`levels` per stack); nothing for level 1, a world without unit levels and reports from before M30. The web shows them as a small "Lv 2" badge next to the unit. **The attacker's copy** has `defenderSide.levels` – the highest level above 1 per defending unit type – only when his scouts were level 2 or more and scouting succeeded (§5.4, *Upgraded scouts*); it is empty otherwise, and in the defenders' copy.
- **Banners (M32b, §21.16):** `ATTACK`, `DEFENSE` and `SCOUT` copies carry a `banners` block when banners did something: the War bonus of the attacker (both copies), the Bulwark bonus of the village (defenders; the attacker only when his scouting succeeded), the banner the reader's side found, the thieves' result (attacker only) and the banner they stole (defenders only).
- `SUPPORT_ARRIVED`: when a support arrives, for its sender and the supported village's owner (one report if it's the same player). `SUPPORT_RETURNED`: when a support is withdrawn or sent back (§6), for the owner of the other village – the one who didn't do it; the units are those sent home. A cancelled support (it never arrived) gives no report, nor does a RETURN arriving home.

## 15. Not in the MVP (ideas for later)

Paladin, archers, morale, night bonus, demolishing, research levels, world end conditions, multiple worlds, premium/automation features, sitters, NPC lords learning from human players (Drivatar-style).

**Dismissing units** – implemented, see §5.2a. The rules as decided (2026-10-04):
- The player dismisses units of one kind from the home stack (only units at home). They **leave the home stack at once**: they no longer defend and can't be sent.
- They are dismissed **one by one at 5× the recruit speed**: time per unit = `secondsPerUnit / 5` of that unit at the producing building's current level (exact rounding and the time for a destroyed building, e.g. "level 1 time", to be fixed then).
- **Nothing is refunded**; each dismissed unit frees its population.
- Cancel puts the units not yet dismissed back into the home stack.
- Open points settled in §5.2a: its own queue per building, lazy completion like recruiting.

## 16. Guilds (M15a, branch `integration/v1-1-features`)

A guild is a named group of players with a tag shown next to their names. (Called *tribes* until M20, for more distance from Tribal Wars. In the first migrations V16 and V17 and in older log entries the word is still `tribe`; V22 renamed everything in the database.) M15a is the membership only; the guild chat is M15b, a political layer on the map M15c (06-milestones). Nothing here changes combat, support, conquest or the market: guild members are not allies in the rules yet.

**Switch.** `guildsEnabled` (world setting, **off by default**, host switches it in the admin world settings or `bannerfall-admin`) and `guildMaxMembers` (2–50, default 10). While off, nothing can be founded, invited to or joined (`409 GUILDS_DISABLED`) and the Guild section is hidden (except for members of an existing guild, who keep it); guilds that exist stay readable. Guilds work in the pre-game too (founding and inviting before the opening); profiles stay closed there, so member names are plain text.

**Name, tag, description.**
- Name 3–30 characters: letters and digits of any language, single spaces and `' . _ -` between; starts and ends with a letter or digit. Tag 2–5 ASCII letters or digits (looks the same everywhere). Both are trimmed.
- Name and tag are unique **ignoring case** (`GUILD_NAME_TAKEN`, `GUILD_TAG_TAKEN`; the unique indexes decide a race).
- Description: at most 500 characters, line breaks allowed, other control characters refused, empty allowed. Only the leader changes it.

**Membership.**
- One guild per player (`ALREADY_IN_GUILD`). NPC lords are never in a guild.
- The founder is the **leader**; a guild has exactly one leader.
- **Invitations only**, no join requests. The leader invites a human player by name, or with the "Invite to guild" button on the player's profile (M34, the same request; `PLAYER_NOT_FOUND` for an unknown name, an NPC lord or a deleted player; `ALREADY_INVITED`, `ALREADY_IN_GUILD`, `GUILD_FULL`). The invited player accepts or declines; the leader can take an invitation back. Accepting drops the player's other invitations, founding a guild too. A full guild refuses invitations and acceptances (`GUILD_FULL`, the last place is decided under a lock). A player gets **one invitation per minute** (from any guild, `429 INVITATION_RATE_LIMIT`; in memory, so a server restart forgets it), so nobody can be flooded by invite / withdraw / invite.
- A member leaves at any time. The leader of a guild with other members must hand the lead over first (`LEADER_MUST_TRANSFER`); the **last member leaving ends the guild**.
- The leader kicks a member (naming himself is a `400`: use leave), hands the lead to a member (he stays a plain member), or disbands the guild for everybody.
- A player the host deletes or bans for good (M14a, M14e) leaves his guild: his invitations go, and a leader's guild passes to its longest member or ends with its last.

**Where the tag shows.** Ranking (tag before the name), profile (guild line linking to the guild), village panel and map tooltip. A guild's points are the sum of its members' points; the guild list is sorted by them.

**Notifications.** The invited player gets `GUILD_INVITED` (toast); every change of a guild's people or text sends `GUILD_CHANGED` to those concerned so their open pages reload. That includes the open invitations: the leader hears when an invitation is declined or dropped because the player joined or founded another guild or was deleted, and an invited player hears when the guild ends.

### 16.1 Guild chat (M15b)

- Every member of a guild sees and writes its own channel next to the world chat. Nobody else sees it, and its messages never show in the world chat.
- Same rules as the world chat (§13): 1–500 characters after trimming, no control characters except line breaks, last 100 messages on load and older ones on request. The one-message-per-second limit is **shared** between the two channels (it is per player, not per channel).
- Writing needs guilds switched on (`GUILDS_DISABLED`) and a guild (`NOT_IN_GUILD`). The history stays readable for members while guilds are off.
- The **leader hides** a message of his guild's channel; the members' open chats drop it at once. The host's hide (admin) works on guild messages too. Nothing is edited.
- A player who leaves or is kicked loses the channel at once: no history, nothing new. A guild that ends takes its messages with it. A member who joins sees the history from before (default picked: a guild is a group of people who share plans; if joiners should to see only what is said after they join, the history query gets a `joined_at` filter).
- Works in the pre-game.

### 16.2 Political layer on the overview map (M15c)

- A display choice only; no rule changes and no server change (the map response already carries `guildId` and `guildTag`, §16).
- The overview map has a switch **Owners | Guilds**, shown while the host has guilds on. *Owners* is the colouring of M11b. *Guilds* draws every village of a guild in that guild's colour, with a soft disc of about 2.5 fields around it, so a guild's villages that lie together show as one region. Players without a guild are drawn faded in the player colour, NPC lords and barbarians as before, the player's own villages with a dark edge so they stay recognisable.
- A guild's colour comes from its id (golden-ratio steps along the hues that are left after leaving out the greens, which vanish on the grass, and the purples, which are the NPC lords' colour; lightness per theme): the same for everybody and on every visit, no stored colour, nothing for the leader to choose. The first eight guilds are at least 15° apart on the colour wheel; beyond that colours start to look alike, which the legend and the guild pages resolve. (If leaders should pick colours, that needs a column and a rule against look-alikes.)
- The discs of one guild are filled as one shape (they merge instead of darkening where they overlap) and are clipped at the map edge. Under the overview a legend lists the (up to eight) guilds with villages in the shown area, most villages first, each linking to the guild's page, and "and N more" when there are more.
- The choice is remembered per device. The detailed map is unchanged.

## 17. Private messages and blocking (M17, branch `integration/v1-1-features`)

Player-to-player messages, one conversation per pair of players. Defaults picked for the open points are marked.

- **Who:** any human player can write to any other human player (NPC lords don't read mail: `404 PLAYER_NOT_FOUND`; writing to yourself is `OWN_PLAYER`). Both parties see the whole conversation; nothing is edited or deleted by a player. A deleted player takes his messages with him: his conversations leave his partners' inbox and unread count, and the conversation reads empty (the rows stay in the database, as the deletion is a soft one; code review 1.2).
- **Text:** the rules of the chat (§13): 1–500 characters after trimming, line breaks and tabs allowed, no other control characters.
- **Limits:** at most one message per second and sender (`MESSAGE_RATE_LIMIT`, `429`, in memory like the chat's), and at most **30 unread messages from one sender** with one recipient (`INBOX_FULL`): the 31st waits until the recipient has read some. The general write rate limit of M14e applies on top.
- **Read state:** a message is unread until the recipient's client says so (`POST …/read`, sent when the conversation is on screen). The unread counter in the navigation counts all unread messages, not those of blocked players.
- **Live:** the recipient gets a `MESSAGE_RECEIVED` notification (toast, counter, and the conversation reloads if it is open; no toast while that conversation is the page on screen).
- **Block list:** a player blocks another player (at most 100). The blocker **no longer sees** anything of that conversation: it leaves the inbox, its unread messages leave the counter, and the history comes back empty; unblocking brings it all back. The blocked player is **told nothing**: his messages are accepted and stored as read, the blocker is not notified, and the 30-unread limit applies to him as if the blocker never read: the 31st message since the block is `INBOX_FULL` (review fix; without it he would see the block in the missing error, and the table would grow without a bound). The blocker can't write to someone he blocked (`PLAYER_BLOCKED`, unblock first). Blocking yourself is `OWN_PLAYER`.
- **Not in v1.1** (defaults, overrulable): messages in the pre-game (the chat covers it), deleting a conversation, reporting a message to the host, message search, messages to a whole guild (that is the guild chat, §16.1).

## 18. Farm helper (M18, branch `integration/v1-1-features`)

A list of targets per village with the units the player usually sends, a one-tap send per target and, since M39, "send all" for the whole list. It saves typing, nothing else: it never sends by itself, every attack follows a tap. (v1.1 had no "send all" on purpose; the owner asked for it for 1.2.) Defaults picked for the open points are marked.

- **List:** per village (the village whose units are sent), at most **50 targets** (`FARM_LIST_FULL`). A target is a village, any village but the player's own (`SAME_VILLAGE`, `OWN_VILLAGE`), once per list (`ALREADY_IN_FARM_LIST`). It stays on the list when its owner changes (a barbarian village that a player conquers is still a target, only the beginner protection starts to count); it disappears when the village does.
- **Template:** the units that are sent, 0 … 30 000 per type, at least one unit, **no nobles and no paladin** (a noble needs a decision each time, a paladin is a general and not a raider; M31). It can be changed at any time. Units don't have to be at home when it is saved.
- **Send:** one tap sends exactly the template as an **attack** from the list's village, through the same code as a normal command (§6): units leave home now, the same errors (`NOT_ENOUGH_UNITS`, `PROTECTED_TARGET` at the arrival time, `OWN_VILLAGE` if the player has conquered the target since …), nothing is sent in part. The attack ends the sender's own beginner protection like any other, unless the target is a barbarian village (§6; the template has no nobles). The entry remembers when it was last sent.
- **Send all (M39):** one button above the list works through it **from the top** (the order the targets were added), each entry exactly like its own Send (its template, an attack, the same rules). It goes on until the list is done or it reaches the first entry whose template the units at home don't cover (`NOT_ENOUGH_UNITS`): **there it stops**, even when a later, smaller template would still fit. Entries whose target can't be hit right now are **skipped** and the run goes on: the target player is under beginner protection at arrival (`PROTECTED_TARGET`) or the target is the player's own village by now (`OWN_VILLAGE`). A skipped entry stops nothing, even if its units would not have been enough either. While the **sender's own beginner protection** lasts, players' villages are skipped as well (default): a single Send of such a target asks twice because it ends that protection, so one tap for the whole list never does; barbarian villages are sent. Every attack that went out stays sent (no all-or-nothing, unlike an attack train); the run holds the village's lock, so nothing else takes its units in between. The answer lists the sent and skipped entries (with the reason) and where it stopped. Two taps (the button turns into "Really send all?", like a protected row's Send) since one tap sends up to 50 attacks; no other confirmation, no cooldown, entries with an attack already on the way are sent again.
- **List display:** distance and one-way travel time of the template (the slowest unit), the target's beginner protection if it still lasts, when it was last sent. No result of the last attack is shown (default; the reports have it).
- **From a report (M38):** a battle report offers the other side's village for the farm list: in the attacker's `ATTACK` and `SCOUT` reports the attacked village, in a `DEFENSE` report (also a supporter's) the attacker's village; never one of the reader's own villages. It opens the farm list of the village that sent the attack or scouts (while it is still the reader's), otherwise the current village's (like the map's "Add to farm list"), with the target filled in, and from an `ATTACK` report also the units that attack sent (without nobles, paladin and thieves) as the template; the player checks and adds it there. A village already on that list gets a link to the list instead.
- **Owner change:** when a village changes hands (§10), its farm list is dropped with its orders; the new owner starts with an empty one.
- **Not in v1.1** (defaults, overrulable): a "send to all" (came in M39, above), result and loot columns, sorting and filters, templates shared between villages, a cooldown or "do not send again for N minutes" warning.


## 19. Achievements (M16, branch `integration/v1-1-features`)

Honours a player earns by playing. They change nothing in the game. Brainstormed on 2026-10-12. **The list of 25 was confirmed on 2026-10-12;** the thresholds below are still drafts, tuned with the playtest numbers (they are numbers in a YAML file, so they can change without a migration).

**Decisions (2026-10-12):**
- **Honour only.** No resources, no perks, no title next to the name, no effect on the ranking.
- **Mixed.** Milestones are earned once; counters have three levels (bronze, silver, gold).
- **Earned is public, progress is private.** Every player profile shows the earned badges; the player's own Achievements page also shows the locked ones with progress. No secret achievements.
- **About 25.** 17 one-time and 8 with levels (below).
- **Ranking: profile only.** The ranking stays points-only; the profile shows the badges and their count.
- **Personal only.** Guilds have no achievements of their own; founding or joining a guild is a personal one-time achievement.
- **Guards: filters plus host revoke** (below).
- **Start at deploy.** If v1.1 goes onto a world that already runs, the counters (wins, defences, loot, units, trade volume) start at zero on that day and nothing is back-filled from old reports. The state-based achievements (points, villages, Town Hall, Academy, guild, days, registered before the opening) are granted from the current state at the first evaluation after the deploy: at the player's next login or visit to the Achievements page (points and villages also at their next change). Foreman is a Town Hall of level 10 in any village now, Scholar any Academy, Pioneer a registration before the world's content was created (`world_settings.initialized_at`). The guild ones need no pass: guilds do not exist before v1.1.

**Who and when.**
- Per world and per human player (a fresh world starts everybody at zero). NPC lords never earn any and show none. A player the host deletes has no profile any more, so nobody sees his badges; the rows stay in the database (the player row stays, §11).
- Granted in the same transaction as the event that causes it, so a rolled-back event grants nothing. Granting is idempotent (a level is earned once).
- The player gets an `ACHIEVEMENT_EARNED` notification (toast) with the name and level. Default picked: no "new" marker beyond the toast.
- Works in the pre-game where the event exists (registering, guilds, chat); profiles are closed there, so nobody sees the badges before the opening.
- Recruiting is lazy (§5.2), so unit counters grow when the village is caught up, and the toast can come a little after the units were finished. "Veteran" (and, on a world that ran before v1.1, Foreman, Scholar and Pioneer from the current state) is checked when the player logs in or opens the Achievements page.

**One-time (17).**

| Key | EN | Earned when |
|---|---|---|
| PIONEER | Pioneer | Registered before the world opened (pre-game) |
| FOREMAN | Foreman | Town Hall reaches level 10 in any village |
| SCHOLAR | Scholar | Completed the Academy |
| SIEGECRAFT | Siegecraft | First ram or catapult finished |
| NOBLE_BLOOD | Noble Blood | First noble finished |
| FIRST_BLOOD | First Blood | First counted attack win (see filters) |
| SHIELD | Shield | First counted successful defence |
| BREACH | Breach | An attack of yours lowers a wall (rams) |
| CONQUEROR | Conqueror | First village conquered |
| TOPPLER | Toppler | Conquered a village from a human player |
| LORD_BREAKER | Lord Breaker | Conquered a village from an NPC lord |
| FALLEN | Fallen | Lost a village to a conquest (not to a host action) |
| PHOENIX | Phoenix | Founded a new village after losing all (respawn) |
| FIRST_DEAL | First Deal | A market offer of yours was accepted, or you accepted another player's |
| FIRST_WORDS | First Words | Sent a message in the world chat |
| GUILD_FOUNDER | Guild Founder | Founded a guild |
| GUILD_MEMBER | Guild Member | Joined a guild (also as its founder) |

Guild ones can only be earned while the host has guilds on.

**With levels (8).** Thresholds are drafts (`gamedata/achievements.yml`), to be tuned with the playtest numbers. None depends on the world speed on purpose: a faster world reaches them sooner.

| Key | EN | Counts | Bronze | Silver | Gold |
|---|---|---|---|---|---|
| GROWTH | Growth | Player points (current, never lost again once earned) | 1 000 | 5 000 | 20 000 |
| LANDLORD | Landlord | Villages owned at the same time | 3 | 8 | 15 |
| STANDING_ARMY | Standing Army | Units finished in recruit orders (scouts and nobles excluded) | 200 | 2 000 | 10 000 |
| VICTOR | Victor | Counted attack wins | 10 | 50 | 250 |
| BULWARK | Bulwark | Counted successful defences | 5 | 25 | 100 |
| PLUNDERER | Plunderer | Resources looted in counted wins | 20 000 | 200 000 | 2 000 000 |
| MERCHANT_PRINCE | Merchant Prince | Resources given through market offers (accepted ones, both sides) | 10 000 | 100 000 | 1 000 000 |
| VETERAN | Veteran | Days since registering | 7 | 30 | 100 |

**Rules for the counters (the filters).**
- A level, once earned, stays even if the value later drops (points, villages).
- **Counted attack win:** the attacker won the battle (§8), sent at least 10 units, and the attacker did not already have a counted win against **that village** within the last `24 h ÷ speed`. A second win on the same village inside that window still happens, it just does not count. Barbarian villages and NPC lords count like any target.
- **Counted defence:** the attacker lost the main battle with at least 10 units sent, and the same attacker did not already have a counted defence against **that village** within `24 h ÷ speed`. The owner of the village gets it; supporters standing there get nothing (only the owner can count a defence). A village the owner has lost in the meantime gives nothing.
- **Loot** only counts for a counted win, and only what was actually taken (§9).
- **Trade volume** counts only what goes through accepted market offers (§12), counted when the offer is accepted, `k × offerAmount` for the offer's owner and `k × wantAmount` for the acceptor. Plain transports ("send resources") count nothing, so gifts to an alt account are worth nothing.
- Support, scouting-only attacks (no attacking units) and attacks on a player's own villages count for none of these.
- **Residual risk (accepted):** two accounts of one person can still trade or attack each other to earn the counters. With honour as the only prize that costs a friend's trust, not game balance.

**Host.** The host revokes an achievement from a player (`POST /api/admin/players/{id}/achievements/{key}/revoke`, CLI `player <name> revoke-achievement <KEY> [--reason …] [--yes]`, 08 §3): all its levels go, the key is blocked for that player (it can't be earned again), and the action goes into the audit log with the reason. Un-revoking is not offered; the host can delete the block row by hand if he must.

**Where it shows.** The player profile (earned badges with a bronze, silver or gold rim, the date on hover or long press; one-time ones have a green rim), and a page for the player's own list `/achievements` (all 25 grouped by category, earned and locked, with progress; a link from the own profile and an entry "Achievements" in the navigation). Texts come in EN and DE like every other screen.

**Banners (M32c).** Six more come with the banners (§21.8, §21.17): Standard Bearer, Colours Raised, Colours Lost and Full Set once, Banner Collector and Banner Thief with levels, in a category of their own. That makes 31 (21 one-time, 10 with levels).

**Not in v1.1** (defaults, overrulable): secret achievements, rewards or titles, an achievement ranking, guild achievements, achievements for private messages, the farm helper or dismissing units, back-dated counters, a "new" marker, sharing a badge to the chat.

## 20. Attack simulator (M23, branch `integration/v1-1-features`)

The request (2026-10-12): an attack simulator at the rally point, to simulate from your own numbers or from recent (for example spy) reports, with the units of the current village easy to paste in.

- **It is the real battle.** `POST /api/simulator` runs `Combat.fight` (§7, §8, §9), the same code real battles use, on numbers sent with the request: attacking units, defending units (everything in the village, own units and supports together), the wall level and, with catapults, the target building and its level. There is no second implementation that could drift. A test fights a real battle through the commands and shows the simulator predicts it exactly. One difference by design: the defender is typed in as **one** stack (the village's own units and the supports added up), while a real battle rounds the losses per stack (§8), so with supports standing in the village the defender's losses of a type can differ from the real battle by a unit or two; strengths, winner, attacker losses, wall and catapult damage do not. There is no morale, night bonus or other modifier in this game (§8), and the world speed doesn't enter a battle, so nothing of that is left out.
- **Luck.** The battle is run three times: with the world's worst luck (`-luckRange`), with none and with the best (`+luckRange`). A world without luck answers with one run. Real luck is random inside that range, so the three runs frame what can happen.
- **What it answers.** Per run: who wins, attack and defence strength, the wall level that counted (rams, §9) and the wall afterwards, the catapult target before and after, the scouting phase (§7.1) if scouts attack, per unit type what dies and what survives on both sides, and the carrying capacity of the surviving attackers (what they actually take depends on the stock, which the simulator does not know).
- **Nobles.** Only a won battle lets the surviving nobles work (§10): every noble lowers the loyalty by 20 to 35, so with `n` nobles the answer is a range, and whether the village falls is `NO` (even the strongest hits leave the loyalty above 0), `YES` (even the weakest hits take it to 0 or below) or `POSSIBLE` in between. The loyalty to start from is typed in (default 100).
- **Unit levels (M30).** The request may carry `attackerLevels` and `defenderLevels` (a level per unit type, missing = 1, 1 to 10, a level above the unit's top level counts as the top). They count only when the world has unit levels on; the web shows a level field per unit in that case and fills it from the player's own research ("My units here", "This village") or from a report (the defender gets the highest level of its stacks, the simulator knows one level per unit type).
- **Reveals nothing.** The simulator reads no village, report or player: it only calculates with what the player sent, so it cannot be used to find anything out about another village (M21 stays intact). It can simulate units and buildings the player does not have; limits only keep the numbers sane (at most 1 000 000 per unit type, levels up to the building's maximum). It needs a login and changes nothing.
- **Banners (M32c, §21.17).** With banners on, the request may carry the attacker's War banner (`warTier`), the target's Bulwark (`bulwarkTier`) and its Banner Hall (`bannerHall`, which caps the Bulwark and makes theft harder). The answer shows both bonuses and, if Banner Thieves attack, how many survive and the chance to steal a banner that flies there. It still reveals nothing: the player types (or takes from his own report) what the target has. **Since the code review 1.2 (D4)** also the attacker's Plunder banner (`plunderTier`, raises the loot capacity) and the target's Oath (`oathTier`, capped by the hall like the Bulwark, lowers every noble's hit to `round(hit × (1 − oath))` in the loyalty outlook). A village flies one banner, so War with Plunder or Bulwark with Oath is refused (400 `VALIDATION`); the form clears the other field.
- **Filling the form (web).** "My units here" takes the units at home into the attacker, "This village" takes the village's defence (units at home plus the supports standing in it), its wall, its loyalty and the level of the catapult target. "Take numbers from a report" lists the newest battle reports (attack, defence, scouting; the 25 newest of them, found among the player's 100 newest reports so that a flood of trade reports doesn't hide them) and a battle report has a "Simulate this attack" link (`?report=`). From a report: the attacker only if there was a real attack (a spy report has no army to attack with: the attacker of the form stays), the defending units and the wall as they were **before** that battle, the catapult target with its level, and the loyalty **before** that battle (the "after" of a conquering battle is 0, which would let any noble conquer). A report from an attack nobody came back from has no defender numbers: those stay as they are.

---

### 21. Banners (M32, branch `integration/v1-2-features`)

Collectable items that give a village a benefit, can be won in battle and can be stolen. Brainstormed on 2026-10-13 (wish of a playtest player, item 6 of the 1.2.0 list). **Signed off on 2026-10-13** ("your defaults sound great and I like the design"), so this is the spec for M32, built after the paladin (M31). Numbers stay first drafts, tuned with playtest data. All numbers are first drafts and live in `gamedata/banners.yml` (changing them needs a new image, no migration), the preview of the look is `banner-sheet.png` in this folder.

## The request (2026-10-13, verbatim ideas)

- Banners give a village **different benefits, one at a time**, depending on the banner and its **tier**.
- **Stealing** an equipped banner works two ways: **conquering the village**, or **winning a battle with a new unit, the banner thief**, which can be part of the attack. One thief gives only a small chance; the more survive, the higher it gets.
- **Getting banners:** winning battles against other players or NPC lords (same probability, attacking or defending), and a small chance when winning against barbarians.
- A thorough design and balance session; new **achievements** for banners owned and banners stolen.

## Summary of the draft

1. **9 banner types × 3 tiers**, one effect each (production, build time, recruit time, attack, defence, travel time, storage, carry, loyalty). Tier III is worth roughly 1–3 levels of the matching building, never a game-changer.
2. A banner is owned by the **player** (his *stash*, safe) and **equipped in one village** (one per village, at risk). Only equipped banners can be stolen.
3. New building **Banner Hall** (max 3): needed to fly a banner, its level caps the tier's effect, it trains the thieves and makes theft harder.
4. **Drops:** a won battle against a player or NPC lord rolls 10 %, against barbarians 1.5 %. Tier I 70 % / II 25 % / III 5 %, type random.
5. **Theft:** conquest takes the village's banner for good. A **banner thief** (new unit, 3 % per surviving thief, capped at 85 %) takes it after a won battle.
6. Three small extras to keep it healthy: **fusing** (3 banners of one tier → 1 of the next), a per-pair roll limit against alt farming, and a host toggle.

## Open points (default in bold, now decided)

| # | Question | Default |
|---|---|---|
| A | A new building (Banner Hall) as the gate for equipping, home of the thieves and guard against theft, or no building (every village has a slot from the start)? | **Banner Hall** (decided 2026-10-13) |
| B | Who sees an equipped banner? | **Only the owner, and whoever scouted the village successfully** (like loyalty, 02 §10, M21). The alternative is a public marker on the map. **Decided 2026-10-13.** |
| C | NPC lords and banners | **Lords don't collect or use banners** (they stay a drop source and can't build the Hall). A banner a lord captures by conquest stays in that village, dormant, and can be taken back by conquest or thieves. **Decided 2026-10-13.** |
| D | Fusing (§4.3) | **In** |
| E | Drop chances 10 % / 1.5 %, tiers 70/25/5, one roll per pair and real day | **as written**, tuned after the playtest |
| F | Do thieves die when a theft succeeds? | **No**, they walk home with the survivors |
| G | Banner of the paladin (carried by the paladin, TW-like) | **No**, per village as you said; the paladin (M31) may carry one later |

---

### 21.1 Concept in rules

- A banner has `type` (9), `tier` (1–3) and an owner (a player). It is either in the owner's **stash** or **equipped** in exactly one village of his (at most one banner per village).
- Banners are **not tradable, not gift-able, not sold**. They only change hands by drop, fusing and theft.
- **Effective tier** of an equipped banner = `min(tier, hallLevel)` of the village's Banner Hall (§21.3). Hall level 0: the banner is **dormant** (no effect) but still sits there and can be stolen.
- Effects belong to the **village**, not to the player: a banner in village A does nothing for village B.
- Banners work in a world only while the host has `bannersEnabled` on (§21.9). Off: no drops, no thefts, no effects; banners stay owned.

### 21.2 The nine banners

Tier values are I / II / III. "Worth" compares tier III with what the same effect would cost in buildings (02 §3, §4.2, §5.2); the aim is that every banner is worth about the same, none is a must-have, and **you pick the banner for the village's job**.

| Key | EN | DE | Effect on the village it flies in | I | II | III | About |
|---|---|---|---|---|---|---|---|
| HARVEST | Harvest | Erntebanner | Production of wood, clay and iron + | 5 % | 10 % | 15 % | ~0.9 mine levels on all three mines (a mine level is +16.5 %) |
| BUILDERS | Builders | Baumeisterbanner | Build time of new build orders − | 5 % | 10 % | 15 % | ~3.3 Town Hall levels of time (HQ is −4.8 % per level); costs unchanged |
| DRILL | Drill | Drillbanner | Recruit time of new recruit orders − | 5 % | 10 % | 15 % | ~2.5 Barracks levels (−6.3 % per level); costs unchanged |
| WAR | War | Kriegsbanner | Attack strength of attacks **sent from** the village + | 3 % | 6 % | 10 % | 100 axemen vs 100 spearmen behind wall 5: 41 → 36 axemen lost |
| BULWARK | Bulwark | Bollwerkbanner | Defence of the village (everybody standing in it) + | 3 % | 6 % | 10 % | same fight: attacker loses 47 instead of 41 |
| HASTE | Haste | Eilbanner | Travel time of attacks and supports **sent from** the village − | 5 % | 10 % | 15 % | 20 fields at 18 min/field: 54 min faster |
| TREASURY | Treasury | Schatzbanner | Warehouse **and** hiding place capacity + | 10 % | 20 % | 30 % | ~1.3 warehouse levels, ~0.9 hiding place levels |
| PLUNDER | Plunder | Beutebanner | Carry capacity of units of attacks **sent from** the village + | 10 % | 20 % | 30 % | 30 % more loot per trip |
| OATH | Oath | Treuebanner | Loyalty each enemy noble takes off the village − | 4 % | 8 % | 12 % | tier III: a 100-loyalty village needs 4.6 nobles on average instead of 4.1 (a noble is 140 000 resources) |

Why these nine: three economy ones (what the request asked for: "different benefits"), two "when I fight" ones, two for the attacker's logistics, one for loot safety, one against conquest. Kept out on purpose for now (ideas): scout veil, merchant capacity, wall bonus, population, a banner per single resource, unique "named" banners.

**When an effect counts (so nothing can be gamed by swapping banners):**
- HARVEST: production is computed lazily from an anchor (02 §3), so equipping, unequipping, stealing and a hall that changes level first **catch the village up**, exactly like a building level change.
- BUILDERS and DRILL: fixed **when the order is queued**, like the Town Hall and Barracks level (02 §4.2, §5.2). Later banner changes don't touch queued orders.
- WAR, HASTE and PLUNDER: **snapshotted when the command leaves** the source village (effective tier at that moment) and stored on the command. A banner stolen or swapped while the army walks changes nothing. A RETURN keeps the factor of the trip it belongs to.
- BULWARK and OATH: read at **battle time** from the defending village's banner. OATH changes only the loss per noble, not the regeneration, so it needs no catch-up.
- TREASURY: capacity only. Taking it away never destroys stock above the new capacity (02 §3 already says production never destroys).

Rounding: capacities `round(base × (1 + b))`, times `max(1, round(time × (1 − b)))` on top of the existing formulas, attack `A × (1 + luck) × (1 + b)`, defence `D × (1 + b)` after the wall term, loyalty hit per noble `round(randomInt(20, 35) × (1 − b))`.

### 21.3 Banner Hall

New building, so the feature has a home, a price and something to catch up on.

| Key | EN | DE | Max | Start | Wood | Clay | Iron | Factor | Pop | Time (s) | Pts | Requires |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BANNER_HALL | Banner Hall | Bannerhalle | 3 | 0 | 300 | 350 | 250 | 1.5 | 8 | 7200 | 20 | HQ 8, Barracks 5 |

- **Level L** (1–3) does three things: (1) a banner can only be **equipped** with level ≥ 1; (2) the banner's effect is that of tier `min(tier, L)`: a tier III banner in a level 1 hall gives the tier I numbers; (3) every level makes **theft harder** by 10 % (§21.6).
- Level 1 also lets the village **recruit banner thieves** (§21.5).
- Catapults can hit it like any building. If it drops below the banner's tier the effect shrinks with it; at level 0 the banner is dormant but not lost.
- It is a normal building otherwise (points, population, build queue, razing rules); NPC lords never build it (their templates don't list it).
- Level 3 cost is 675 / 788 / 562 (sum of all three levels about 1 400 / 1 650 / 1 190), comparable to a Stable or Smithy, and clearly cheaper than anything that touches nobles.

### 21.4 Stash, equipping, fusing

#### 21.4.1 Stash and equipping
- **Stash** = the player's banners not equipped anywhere. Safe from theft, no limit (they do nothing, so hoarding gains nothing; fusing is the sink).
- **Equip** a stash banner in one of his villages with a Banner Hall ≥ 1 and an empty slot, **unequip** back to the stash, or **swap** (unequip + equip in one go). Instant, free. Catch-up before any change (§21.2).
- **Locked while an attack is on its way to that village** (`BANNER_LOCKED`, same count as `incomingAttacks`, the same idea as `DISMISS_LOCKED`, 02 §5.2a): otherwise the banner could be hidden in the stash the moment a thief army is seen. New equipping of an empty slot is **also** locked then, so a defender can't pull a better banner out in the last minute.
- A village that is conquered loses its banner (§21.6.1). A village that falls to barbarians (host ban or delete, 02 §10, M14e) loses it for good: the banner is **destroyed**, as is the stash of a deleted player.

#### 21.4.2 Fusing (proposal D)
- **3 banners of the same tier from the stash → 1 banner of the next tier**, the result takes the type of **one of the three** (the player chooses). Tier III can't be fused. Equipped banners can't be fused.
- Instant, free. It turns the many tier I drops into something useful, keeps the type choice in the player's hands (otherwise 9 random types make a matching triple rare) and keeps the stash small.
- Fusing counts a **new banner** for the owner: it gets a new id (the achievement "distinct banners stolen", §21.8, isn't gamed by fuse and steal).
- Built in M32c: the request, the checks and the web card are in §21.17.

### 21.5 The banner thief

| Key | EN | DE | Building | Requires | Wood | Clay | Iron | Pop | Att | Def inf | Def cav | Type | Speed | Carry | Time (s) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BANNER_THIEF | Banner Thief | Bannerdieb | BANNER_HALL | Banner Hall 1 | 100 | 120 | 150 | 2 | 0 | 3 | 3 | INF | 18 | 0 | 1500 |

- Joins the enum **after NOBLE** (the canonical unit order, 02 §5.1), recruiting works like every other unit (02 §5.2), the queue is the Banner Hall's own.
- **Attack 0, defence 3:** thieves add nothing to a battle and die in it like scouts: they take the attacker's loss ratio (02 §8) with everyone else. They are not support material and defend nothing.
- **Speed 18** (same as spearman and axeman): a thief never slows an infantry army, but it does slow a pure cavalry raid (10 min/field) down to 18. That is the price of bringing them.
- A pure thief attack (no attacking units) has no main battle, so it can't win and steals nothing; it is just a scouting-free probe. Thieves with scouts or other units work normally.
- They are **expensive per head on purpose**: 20 thieves are 40 pop and about 2 000 / 2 400 / 3 000 resources, a real investment next to a normal raid.

### 21.6 Stealing banners

An equipped banner (also a dormant one) can change hands in two ways. Both only exist against **villages of players and NPC lords**; barbarian villages carry no banner.

### 21.6.1 Conquest
- When a village is conquered (02 §10) its banner goes to the **conqueror's stash**, whatever its tier and effect, **intact** (type and tier stay). The new owner can equip it in any village with a hall.
- **NPC lord conquers a village with a banner** (proposal C): lords have no stash, so the banner **stays in the village**, now dormant (no hall). Taking the village back, or a thief attack that wins, brings it back into the hands of a human.
- The conquest reports (`CONQUEST`, `VILLAGE_LOST`, 02 §14) show which banner moved.

### 21.6.2 Theft with thieves
After a **won main battle** (02 §8) of an attack that carried thieves, if the target village is not conquered by this battle (a conquest takes the banner anyway), has an equipped banner and `k ≥ 1` thieves **survived**:

```
pSurvivors = 1 − (1 − 0.03)^k
p          = min(0.85, pSurvivors × (1 − 0.10 × hallLevelOfTarget))
steal if randomDouble() < p
```
- One `RandomSource` draw, like luck (02 §7).
- On success the banner goes to the attacker's **stash** and leaves the slot. The thieves are **not** consumed (proposal F) and walk home with the survivors.
- Only one banner per village exists, so one battle steals at most one banner.
- Barbarian targets, lost battles, scouts-only attacks, attacks on the sender's own villages, and villages without a banner: no roll.

| Surviving thieves | Hall 0 | Hall 1 | Hall 2 | Hall 3 |
|---|---|---|---|---|
| 1 | 3 % | 3 % | 2 % | 2 % |
| 5 | 14 % | 13 % | 11 % | 10 % |
| 10 | 26 % | 24 % | 21 % | 18 % |
| 20 | 46 % | 41 % | 36 % | 32 % |
| 30 | 60 % | 54 % | 48 % | 42 % |
| 50 | 78 % | 70 % | 63 % | 55 % |
| 80+ | 85 % | 82 % | 73 % | 64 % |

**How a defender protects a banner:** keep the army so strong the attack loses (no thief survives a lost battle); a higher hall; unequip the banner while no attack is coming (it earns nothing then); or accept the risk for the benefit. **How an attacker finds one:** a successful scouting report shows the village's banner and hall (§21.7), so sending thieves blind is a gamble.

### 21.7 Drops: how banners are won

After the main battle of an attack (not scouts-only) and **in the same transaction**, like achievements (§19), the **winner** rolls once for a banner:

| Winner and opponent | Who rolls | Chance |
|---|---|---|
| Attacker won against a **player** village | the attacker | 10 % |
| Attacker won against an **NPC lord** village | the attacker | 10 % |
| **Defender won** against a player or NPC lord attack | the **village owner** (not supporters) | 10 % |
| Attacker won against a **barbarian** village | the attacker | 1.5 % |

Same chance for players and lords as requested; barbarians "a small chance". The host multiplies all of them with `bannerDropFactor` (§21.9).

**Eligible battles (filters, so farming and feeding don't pay):**
- **Size:** the **losing** side must have fought with at least **30 population** of units (the attacker's sent units, or the defender's units in the village before losses; against barbarians the attacker's army must be at least 30 pop). Beating an empty village or a lone probe rolls nothing.
- **One roll per pair per real day:** the same winner against the same loser (player, NPC lord, or one barbarian village) rolls at most **once per 24 real hours** (not scaled by speed: it protects the player's day, like the NPC attack caps, 09 §7). The reverse direction is its own pair.
- **At most 8 drops per player per real 24 hours** as a safety net; normal play never hits it.
- Not eligible: attacks on one's own villages. A battle that conquers the village **does** roll (the banner that moves with the conquest and the drop are independent).
- A player under beginner protection may win against barbarians and roll (M25), nobody can attack him.

**What drops:** tier I 70 %, II 25 %, III 5 %; the type is uniformly random among the nine. The banner goes to the winner's stash and he gets a toast (`BANNER_FOUND`) and a line in the battle report.

**Expected supply (rough, to be measured in the playtest):** an active player at speed 6 attacks real targets a few times a day and gets attacked now and then. With these filters that is about **0.3–0.6 drops a day from players and lords** and about **0.15–0.45 a day from farming barbarians** (30 barbarian villages in range, 1 roll each per day): roughly **one banner a day**, about 70 % tier I. In the one-week playtest that is 5–8 banners per active player, enough to try several types, with fusing as the way up.

### 21.8 Achievements (additions to §19)

Same rules as §19 (honour only, granted in the same transaction, public when earned, thresholds in `achievements.yml`). **Not** NPC lords.

**One-time (4):**

| Key | EN | Earned when |
|---|---|---|
| STANDARD_BEARER | Standard Bearer | First banner found |
| COLOURS_RAISED | Colours Raised | First banner **active** in a village (equipped, effect above 0) |
| COLOURS_LOST | Colours Lost | A banner of yours was stolen or went with a conquered village |
| FULL_SET | Full Set | Has owned each of the nine banner types |

**With levels (2):**

| Key | EN | Counts | Bronze | Silver | Gold |
|---|---|---|---|---|---|
| BANNER_COLLECTOR | Banner Collector | Banners owned at the same time (stash and equipped) | 3 | 10 | 25 |
| BANNER_THIEF | Banner Thief | **Distinct** banners taken from other players' villages by thieves or conquest | 1 | 5 | 20 |

Filters: BANNER_THIEF counts a banner **once per player**, by banner id, and **not** a banner the player owned before (taking back your own banner counts for nothing), so two accounts passing one banner around earn nothing after the first time. A banner taken from a dormant NPC-lord village counts if the player never owned it before. A fused banner is a new banner (§21.4.2). Back-fill on a running world: BANNER_COLLECTOR and FULL_SET start from the current state (empty), nothing else. The ranking stays points-only.

### 21.9 World settings, host, bookkeeping

- **World settings (admin page, tab World, `bannerfall-admin world set`):** `bannersEnabled` (default **true**), `bannerDropFactor` (0–3, default 1.0; 0 = no drops, theft and conquest transfer still work). Everything else is in `gamedata/banners.yml`.
- **Off:** no drops, no thefts (conquest then leaves the banner in the village), no effects, no new Hall or thieves; owned banners stay.
- **CLI and admin API:** `banner list <player>`, `banner grant <player> <TYPE> <tier>` (cheats-style help, audit log with reason), `banner revoke <id>` (audit log). Deleting a player destroys his stash.
- **Data (sketch for 03):** table `banner` (id, owner player, type, tier, equipped village unique and nullable, created at, origin FOUND / FUSED / GRANTED), table `banner_event` (banner, kind FOUND / EQUIPPED / UNEQUIPPED / STOLEN / CONQUERED / FUSED / DESTROYED, from and to player, battle report, at) which also serves the pair limit and the achievements, world settings columns, `movement` gets the snapshotted factors (attack, carry, travel), one migration (V24 or later).
- **Reports (02 §14):** `ATTACK` and `DEFENSE` get a banner block: found (winner), theft with the chance and result for the attacker, stolen for the defender. The attacker's report shows the chance used so the rule is learnable; it doesn't show the roll. A scouting that succeeds adds the village's banner (type, tier) and hall level.
- **Notifications:** `BANNER_FOUND`, `BANNER_STOLEN` (toasts), reports as usual.

### 21.10 How banners look

See `banners/banner-sheet.png` (all 27, light theme) and `banners/banner-sheet.svg` in this folder (docs/plan/banners/).

- **Form:** a hanging standard on a crossbar, drawn the same way for everything. **Tier is readable without colour:** tier I has a straight lower edge, bronze trim and one pip; tier II a swallow-tail, silver trim, tassels and two pips; tier III gold trim, a gold finial on the pole, long tassels and three pips. The pips also work at tiny sizes.
- **Type is readable without colour too:** nine simple white emblems (ear of wheat, hammer, crossed spears, axe, tower, arrow with speed lines, coin, sack, crown) on a field colour from the existing palette (green, brown, blue, crimson, slate, teal, amber, plum, ivory). The map's crossed swords (02 §6) stay unique to attacks, so War uses an axe.
- **Built as one React component** `<Banner type tier size>` that draws the SVG from the nine emblem paths and three tier styles: no 27 image files, dark mode works with the same colours (a thin ink outline carries it), `aria-label` "War banner, tier II". Sizes: 20 px in lists and reports, 56 px on the Banner Hall page, 96 px in the stash.
- **Where it shows:** the Banner Hall page (slot, effect, theft chance for the owner, stash with equip and fuse), an own-village badge in the village panel with tooltip, the reports, the profile only through achievements. **No map marker** (proposal B).
- Texts EN and DE like everything else (names above).

### 21.11 Balance notes

- **Snowball:** strong fighters win more battles, so they roll more. Counter: effect sizes are small (tier III ≤ 10–15 %), the Hall caps what a banner is worth, theft moves banners from the strong to those who took the risk, the pair limit and the 30-pop size filter keep the supply flat. If playtest numbers say the leaders pull away, the first knobs are the drop chance and the Hall's tier cap.
- **Alt farming:** two accounts of one person can still trade rolls (each wins as defender once a day). With the 30-pop minimum losing side and the real-day pair limit the yield is at most ~0.2 banners a day per pair and it costs real units. Accepted, like §19. Banners can't be given, which closes the easy route.
- **NPC lords:** they don't use banners or thieves at all (proposal C), so their balance (09 §7, §11) doesn't change; their intel and `Combat.fight` get the banner multipliers as inputs (the lords' estimator reads the banner from a scouting report, with no banner assumed otherwise; the safety margin of 09 §6.1 covers a ≤ 10 % miss).
- **What the playtest should answer:** how many banners a player gets per day; does anybody build thieves (unit counts, `bannerfall-admin` statistics); are tier III banners too common; do players unequip to hide; is `Haste` or `War` always the pick. All numbers above are tunable in the YAML.

### 21.12 What changes in other docs and code

- **02:** §4.1 (Banner Hall), §5.1 (Banner Thief), §6 (command factors, Haste), §8 (War, Bulwark), §9.3 (Plunder carry), §10 (Oath, banner on conquest), §3 (Harvest, Treasury), §4.2/5.2 (Builders, Drill), §14 (reports), §19 (achievements), §1 (settings), new §21 (this text).
- **Code hooks:** `Combat.fight` gets two multipliers (`attackBonus`, `defenceBonus`) in `BattleInput`; `Production.advance` the factor; `Loot.capacity` the carry factor; `Loyalty.afterNobles` the reduction; `BattleService.resolve` the drop and theft rolls and the banner moves; `ConquestService.conquer` moves the banner; `BuildService`, `UnitService` the time factors; command creation snapshots the three factors.
- **Attack simulator (M23):** optional inputs for the attacker's War banner, the defender's Bulwark banner, the hall level and the thief survivors, so the player sees the effect before sending.

### 21.13 Suggested split of M32 (each with its own review as usual)

1. **M32a Foundations:** Banner Hall, banner and stash tables, equip / unequip / swap with the lock, the economy effects (Harvest, Builders, Drill, Treasury), the `<Banner>` component, the Banner Hall page, settings and admin CLI.
2. **M32b Battle:** War, Bulwark, Haste, Plunder, Oath snapshots and hooks, drops with filters, thief unit, theft and conquest transfer, reports and toasts.
3. **M32c Finish:** fusing, achievements, simulator inputs, NPC estimator inputs, docs, hub changelog entry.

### 21.14 Not in M32 (ideas, overrulable)

Banner trading or gifting, banners on the paladin, banner effects that scale with Hall level beyond the tier cap, NPC lords using or stealing banners, a public banner marker on the map, scout veil / merchant / wall / population banners, named unique banners, a pity timer for drops, thieves that defend (counter-thieves), banners as guild property, a banner ranking.

### 21.15 Status and what M32a built (Claude, 2026-10-13, branch `feature/m32a-banner-foundations-r9bta5`)

M32a is the first of three parts (§21.13). The open points A to G are decided with the defaults of the table above. **In M32a:** the Banner Hall, the stash and slot tables, equip / unequip / swap with the lock, the four economy effects (Harvest, Builders, Drill, Treasury), the world settings `bannersEnabled` and `bannerDropFactor`, host grant / list / revoke, the `<Banner>` component and the Banner Hall page, the banner in a successful scouting report. **Not yet (M32b, M32c):** War, Bulwark, Haste, Plunder and Oath have their numbers and their slots but **do nothing yet**; drops, the thief unit, theft, fusing, achievements, the battle report blocks, the simulator and NPC estimator inputs, the hub changelog. (M32b built the battle part, §21.16.)

Where the spec was silent, M32a picked these defaults (open to change):

- **Slot data.** `banner.equipped_village_id` (unique, null = stash) is the only link between a banner and a village; there is no column on `village`. Looking it up costs one indexed query, and only for a village with a Banner Hall of level 1 or more (§21.3 says hall 0 means dormant anyway).
- **Order of the checks when equipping:** `BANNERS_DISABLED`, `BANNER_NOT_FOUND` (not his), `BANNER_NOT_IN_STASH` (already flying), `BANNER_HALL_REQUIRED`, `BANNER_LOCKED`. Unequipping: `NO_BANNER_EQUIPPED`, then `BANNER_LOCKED`. Equip, swap and unequip all lock the village, then the player row (village before player, like the paladin slots), and catch the village up first.
- **Dormant banners.** A banner works only in a village of a **human** player with a Banner Hall of level 1 or more and only while `bannersEnabled` is true. In a village an NPC lord holds (§21.8) or without a hall the village behaves as if no banner flew there. The owner still sees the banner on his village page, marked as asleep, so he learns why.
- **World with banners off.** The Hall cannot be built (`BANNERS_DISABLED`) and is hidden in the Town Hall and on the village page unless one stands there; equipping is refused; existing banners and halls stay and sleep (the owner's village page and Banner Hall page show them with effective tier 0). A conquest takes nothing then (§21.9): the banner stays in the village and passes with it to the conqueror, asleep until banners are on again (review, 2026-10-13).
- **Conquest transfer and destroying came forward from M32b** so that M32a never leaves an inconsistent state: when a village changes owner by conquest its banner goes intact into the conqueror's stash (an NPC lord conqueror keeps it in the village, dormant); a host delete or a permanent ban destroys all banners of the player. Both write a `banner_event` (`CONQUERED`, `DESTROYED`).
- **Rounding** (§21.2): capacities `round(base × (1 + b))`, times `max(1, round(seconds × (1 − b)))`, production `perHour × (1 + b)` without rounding; all three in `rules.BannerRules`. The Treasury also raises what a loot run cannot take (the hiding place capacity).
- **Scouting.** A successful scouting report carries `scouting.banner = {type, tier}` (the village's hall level is in the building list). A failed scouting, the defender's copy and a village without a banner show none. M32b adds the ATTACK and DEFENSE banner blocks.
- **Admin.** `banner list <player>`, `banner grant <player> <TYPE> <tier> [--reason]`, `banner revoke <id> [--reason] [--yes]` (08); every grant and revoke writes an audit row (`GRANT_BANNER`, `REVOKE_BANNER`) and a `banner_event` line.
- **Invariants.** The invariant check's capacity bound (twice the plain capacity) already covers a tier III Treasury banner (+30 %).

### 21.16 What M32b built (Claude, 2026-10-13, branch `feature/m32b-banners-in-battle-n64wm6`)

**In M32b:** War, Bulwark, Haste, Plunder and Oath work; drops with all filters of §21.7; the Banner Thief unit (recruited in the Banner Hall) and theft (§21.6.2); the banner block in ATTACK, DEFENSE and SCOUT reports and the banner line in CONQUEST and VILLAGE_LOST reports; the toasts `BANNER_FOUND` and `BANNER_STOLEN`. Migration V28. **Left for M32c:** fusing, the banner achievements, the simulator and NPC estimator inputs, the hub changelog entry.

Where the spec was silent, M32b picked these defaults (open to change):

- **Enum position.** `BANNER_THIEF` comes **after PALADIN**, not straight after NOBLE as §21.5 says: the paladin was appended after the noble in M31 and the enum is never reordered (stored columns and API orders follow it).
- **Snapshots.** An ATTACK keeps War, Plunder and Haste of its source village as they were when it left (`movement.banner_attack / banner_carry / banner_haste`), a SUPPORT only Haste. The RETURN after a battle keeps the trip's Haste and carries the trip's numbers along. A **withdrawn or sent-back support** walks home **without** Haste (it is a new move ordered from the other end); a cancelled command keeps the time-it-was-on-the-way rule (§6). The snapshot only counts while `bannersEnabled` is still on at arrival: the host switch wins.
- **Battle-time banners.** Bulwark and Oath are read from the defending village when the battle is fought (effective tier, §21.2: a small hall caps it, a dormant banner gives nothing). The farm list's travel times use the village's Haste too.
- **Theft details.** The target's hall level is read **after** the battle's catapult damage. Order of the checks: battle lost → `BATTLE_LOST`; village conquered → `CONQUERED`; no thief survived → `NO_SURVIVORS` (checked before the banner, so dead thieves reveal nothing); no banner there (or a barbarian village) → `NO_BANNER`; otherwise the roll → `STOLEN` or `FAILED` (both show the banner and the chance). Thieves alone have attack 0, so there is no main battle: the attacker gets a `SCOUT` report (outcome `SCOUTS_ONLY`, theft `BATTLE_LOST`) and the web says there was no battle.
- **Drop details.** The losing side's population: the attacker's sent army, or every unit standing in the village before the battle (supports included); against a barbarian village the attacker's army. A skipped roll (too small, daily cap) doesn't use up the pair's 24 h. The pair window is one upsert on `banner_roll`; the daily cap counts `FOUND` events of the last 24 real hours. NPC lords never roll. The banner's `FOUND` log line names the battle's village. Draw order in a battle: luck, the nobles' loyalty hits, the theft roll, the drop roll (and tier and type when it drops).
- **Report visibility.** War: both copies. Bulwark: the defenders' copy, the attacker's only when his scouting succeeded (like the scouted banner). Found: each side's own copy. Theft: the attacker's copy. Stolen: the village owner's copy. Supporters get their own `DEFENSE` copy without Found and Stolen (review: the owner's banner is his business, §21.1). Reports without any of these have `banners: null`.
- **Toasts.** `BANNER_FOUND` to the finder; `BANNER_STOLEN` to the robbed owner (`lost: true`, not to an NPC lord) and to the thief's owner (`lost: false`).
- **Banners off.** The thief is hidden (recruit list, Banner Hall tab, smithy) and refused (`BANNERS_DISABLED`); thieves already trained stay and fight as attack-0 units but steal nothing; no drops. The farm list refuses thieves in a template (400), like nobles and the paladin.
- **NPC lords.** Their templates, unit lists and intel never contain thieves (`NpcTemplateValidation` refuses them); the estimator ignores banners (M32c adds the inputs).

### 21.17 What M32c built (Claude, 2026-10-13, branch `feature/m32c-banner-fusing-achievements-8yq3yf`)

**In M32c:** fusing (§21.4.2), the six banner achievements (§21.8), the banner inputs of the attack simulator (§20) and the scouted Bulwark in the NPC lords' estimator (09). Migration V29. With this, every part of §21 is built; the numbers stay the drafts of §21.11 until the playtest says otherwise.

Where the spec was silent, M32c picked these defaults (open to change):

- **Fusing, the request.** `POST /api/banners/fuse` names exactly three different stash banners and the type the new banner takes. The checks, in this order: banners off → `BANNERS_DISABLED`; not three different ids → `400 VALIDATION`; one of them not the player's (or gone) → `BANNER_NOT_FOUND`; one of them flies in a village → `BANNER_NOT_IN_STASH`; tiers differ → `BANNER_TIERS_DIFFER`; tier III → `BANNER_TIER_MAX`; a type none of the three has → `BANNER_FUSE_TYPE`. Only the player row is locked (stash banners fly nowhere, equipping locks the player row too). Nothing is locked by an incoming attack: stash banners can't be stolen anyway.
- **Fusing, the bookkeeping.** The new banner gets a new id and origin `FUSED`. The log gets a `FUSED` line per part (from the player, to nobody, detail "into &lt;new id&gt;") and one for the new banner (from nobody, to the player, detail "from &lt;a&gt;, &lt;b&gt;, &lt;c&gt;"); the three parts are then deleted. The daily drop cap still counts only `FOUND` lines, so fusing never blocks a drop. Since V29 every log line keeps the banner's type and tier, so the log still knows them after the banner was fused or destroyed.
- **Fusing, the web.** A card "Fuse banners" on the Banner Hall page, below the stash, only while banners are on: the stash banners grouped by tier (I and II; a tier with fewer than three says so), toggle buttons to pick three, picking a banner of another tier starts a new choice. With three picked, radio buttons offer the types of the three (the first in the order of §21.2 is preselected), then a confirmation sheet and a success line. No undo.
- **Achievements, when.** Standard Bearer when a drop gives the player a banner (not for a host grant, not for a steal). Colours Raised when the player equips a banner (the hall is at least 1 and banners are on, so it works at once). Colours Lost for the village's owner when thieves steal his banner or a conquest takes it (not when the host revokes or a ban destroys it; never for an NPC lord). Banner Thief for the taker of a stolen or conquered banner that goes to his stash (a conquest by an NPC lord keeps the banner in the village and counts nothing). Banner Collector counts the banners owned now, stash and equipped; Full Set counts the types of the banners owned now plus every type a log line ever gave the player (found, granted, stolen, conquered, fused). Both are checked whenever a banner comes to the player, at login and on the Achievements page.
- **Achievements, "owned before".** Banner Thief looks for an earlier log line that gave this banner (by id) to the same player; one means "had it before" and the take counts nothing. A fused banner has a new id, so a banner fused from stolen ones and stolen back by someone else counts for him as new, and taking back your own unfused banner counts for nothing.
- **Achievements, banners off.** No banner moves, so none is earned; already earned ones stay. Granting stays honour only and in the same transaction as the event (a rolled-back battle grants nothing). They are in a sixth category **Banners** on the Achievements page (after Conquest), 31 achievements in all.
- **Back-fill.** A world running M32b before M32c gets Banner Collector and Full Set at the player's next login or visit to the Achievements page (from the banners he holds and the log lines that gave him banners; the lines written before V29 got their type and tier from the banner where it still exists). Standard Bearer, Colours Raised, Colours Lost and Banner Thief start at zero, as §21.8 says.
- **Simulator.** The request takes `warTier` (the War banner of the attacking village, 0 to 3), `bulwarkTier` (the Bulwark in the target, 0 to 3) and `bannerHall` (the target's hall, 0 to the building's maximum); missing means none. The hall caps the Bulwark like a real village (§21.2) and is the catapults' target level when they aim at the Banner Hall. The War tier is taken as it is (the player types the tier that works in his village). The answer names both bonuses once and per run the thieves sent and surviving and the theft chance (§21.6.2, with the hall after the catapults); it is 0 when the battle is lost or no thief survives, and without thieves there is no theft row. A world with banners off ignores all three inputs and shows none of it. Code review 1.2 (D4) added `plunderTier` (with the loot capacity) and `oathTier` (capped by the hall, with the loyalty outlook); the answer names them as `plunderBonus` and `oathBonus`.
- **Simulator, filling the form.** "My units here" takes the War banner of the current village at the tier its hall lets it work; "This village" takes its Bulwark (as it is) and its hall; "Clear" resets them. From a report: the War banner from the report's attack bonus; the Bulwark and the hall from a successful scouting (the hall from the scouted buildings); without one, the Bulwark the defenders' copy names, in a hall just big enough for it. Turning a report's percent back into a tier uses the effect table of `GET /api/banners`.
- **NPC estimator.** A successful scouting now also keeps the scouted banner in the lord's memory (a later scouting replaces it, a failed one keeps the old). The estimate adds the Bulwark at the tier the scouted hall allows, only while banners are on; any other banner type adds nothing. Lords still never use banners or thieves. Thieves among the scouted troops count as defenders with their 3 defence (attack 0 changes nothing in a defence), which a test pins down. The estimate fights without unit levels, as before.
