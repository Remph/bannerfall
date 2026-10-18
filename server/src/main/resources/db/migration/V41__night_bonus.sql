-- 1.4: the night bonus (docs/plan/02-game-rules.md §8.1). Owned villages defend nightBonusFactor
-- times as strongly against attacks arriving from the start hour to the end hour (German time by
-- default). Off by default: the host switches it on.
alter table world_settings add column night_bonus_enabled boolean not null default false;
alter table world_settings add column night_bonus_start_hour integer not null default 23
    check (night_bonus_start_hour between 0 and 23);
alter table world_settings add column night_bonus_end_hour integer not null default 7
    check (night_bonus_end_hour between 0 and 23);
alter table world_settings add column night_bonus_factor double precision not null default 2.0
    check (night_bonus_factor >= 1);
alter table world_settings add constraint world_settings_night_bonus_hours
    check (night_bonus_start_hour <> night_bonus_end_hour);
