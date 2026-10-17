-- v1.3: how a world is won and ends (docs/plan/02-game-rules.md §22, 03-data-model.md).

-- The victory condition the host picked (DOMINATION, KEEPS, NONE) and its numbers. A world that
-- already runs gets domination like a new one; no end date until the host sets one.
alter table world_settings
    add column victory_mode         varchar(16) not null default 'DOMINATION',
    add column domination_percent   int         not null default 60,
    add column victory_hold_hours   int         not null default 48,
    add column keep_count           int         not null default 5,
    add column keep_percent         int         not null default 51,
    -- The end date (null = none) and when the world ended (null = it runs; set = frozen).
    add column ends_at              timestamptz,
    add column ended_at             timestamptz,
    -- The side that meets the victory condition at the last check ('GUILD:12', 'PLAYER:5') and
    -- since when without a break; both null = nobody.
    add column victory_leader       varchar(32),
    add column victory_leader_since timestamptz;

-- Banner keeps: villages around the map centre that count for the KEEPS victory.
alter table village add column keep boolean not null default false;

-- The result of an ended world: one row, written when it ends, deleted if the host takes the end
-- back. Names are copied so the result stays as it was even if a guild is renamed or ends later.
create table world_result (
    id            smallint    primary key check (id = 1),
    reason        varchar(16) not null,          -- DOMINATION / KEEPS / END_DATE
    ended_at      timestamptz not null,
    winner_kind   varchar(8),                    -- GUILD / PLAYER; null = nobody held a village
    winner_ref_id bigint,
    winner_name   varchar(64),
    winner_tag    varchar(8),
    winner_npc    boolean     not null default false
);

-- The final standings of the sides (best first), as they were when the world ended.
create table world_result_side (
    rank     int         primary key,
    kind     varchar(8)  not null,
    ref_id   bigint      not null,
    name     varchar(64) not null,
    tag      varchar(8),
    npc      boolean     not null,
    villages int         not null,
    points   bigint      not null,
    keeps    int         not null
);
