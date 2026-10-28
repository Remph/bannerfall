-- 1.7: the in-game tutorial (docs/plan/02-game-rules.md §27). tutorial_state has a row for every
-- player who has the tutorial switched on or has ever had it (no row = never started, so every
-- player from before 1.7 starts without it). tutorial_progress holds one row per player and step
-- that is done or skipped; the unique key makes a refund pay once.

alter table world_settings add column tutorial_enabled boolean not null default true;

create table tutorial_state (
    player_id bigint  primary key references player (id) on delete cascade,
    active    boolean not null
);

create table tutorial_progress (
    id        bigint generated always as identity primary key,
    player_id bigint      not null references player (id) on delete cascade,
    step_key  varchar(32) not null,
    status    varchar(8)  not null check (status in ('DONE', 'SKIPPED')),
    wood      int         not null default 0,
    clay      int         not null default 0,
    iron      int         not null default 0,
    done_at   timestamptz not null,
    unique (player_id, step_key)
);
