-- 1.6: how far apart the sides start (docs/plan/02-game-rules.md §2). The factor scales the spawn
-- radii, the barbarian and keep rings and the NPC lords' radii; existing worlds keep NORMAL, which is
-- the spread of every world before.
alter table world_settings add column map_spread varchar(6) not null default 'NORMAL'
    check (map_spread in ('CLOSE', 'NORMAL', 'WIDE', 'VAST'));
