-- 1.6: morale (docs/plan/02-game-rules.md §8.2). An attack on a much smaller player hits with less
-- strength: 0.3 + 3 x defender points / attacker points, at most 100 %, at least the floor (in
-- percent). On by default, and on for every existing world too.
alter table world_settings add column morale_enabled boolean not null default true;
alter table world_settings add column morale_floor integer not null default 30
    check (morale_floor between 30 and 100);
