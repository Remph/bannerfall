-- 2.1 S1: the solo world (docs/plan/12-solo-and-allies.md §1 to §3, 02-game-rules §28).
-- solo_mode: a world for one human and the NPC lords; it sleeps while no human has the game open.
-- asleep_since: when the world fell asleep or was paused, null while it runs; the game time stands
-- still at it until the wake moves every game clock on by the time asleep (FrozenTime).
-- paused: the host's pause button; a paused world stays asleep until the host presses play.
-- clock_speed: the host's fast forward (1 = normal, 2 or 4 = every 10 seconds the game clocks move
-- on by another 10 or 30 seconds).

alter table world_settings
    add column solo_mode    boolean     not null default false,
    add column asleep_since timestamptz,
    add column paused       boolean     not null default false,
    add column clock_speed  smallint    not null default 1 check (clock_speed in (1, 2, 4)),
    add constraint world_settings_paused_asleep check (not paused or asleep_since is not null);
