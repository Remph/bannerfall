-- M29: barbarian walls as world settings (docs/plan/02-game-rules.md §11). The defaults keep a
-- world as it was: walls off = a new barbarian village still starts with a random wall 0..2 and
-- never builds one. Switched on, new barbarian villages start without a wall and the growth round
-- builds one level per round from `barbarian_wall_start_days` after the opening on, up to
-- `barbarian_wall_max_level`.

alter table world_settings add column barbarian_walls_enabled boolean not null default false;
alter table world_settings add column barbarian_wall_start_days int not null default 3;
alter table world_settings add column barbarian_wall_max_level int not null default 5;
alter table world_settings add column barbarian_walls_rebuild boolean not null default false;

-- A ram knocked this barbarian village's wall down at least once. Without "barbarians rebuild
-- walls" such a wall stays down. Cleared when the village turns barbarian again.
alter table village add column wall_razed boolean not null default false;
