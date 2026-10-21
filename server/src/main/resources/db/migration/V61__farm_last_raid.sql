-- Farm list overhaul (1.6): each entry remembers the result of the latest attack from its village
-- at its target that landed (docs/plan/02-game-rules.md §18), so the list shows the last haul next
-- to what the units can carry. All null until the next attack lands; set by BattleService.
alter table farm_target add column last_raid_at timestamptz null;
alter table farm_target add column last_raid_won boolean null;
alter table farm_target add column last_raid_loot integer null;
alter table farm_target add column last_raid_capacity integer null;
