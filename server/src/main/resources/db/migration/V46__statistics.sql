-- Statistics (1.5, 02-game-rules §24): running totals per player and a daily history.

-- What a player did, counted from this migration on: battles, kills, loot, conquests, recruited
-- units, finished building levels. One row per player and stat (`Stat` enum name), raised with one
-- upsert in the transaction of the event, so parallel events add up exactly. Humans and NPC lords;
-- barbarians have no player. Unlike achievement_counter nothing is filtered.
create table player_stat (
    player_id bigint not null references player (id) on delete cascade,
    stat      text   not null,
    value     bigint not null,
    primary key (player_id, stat)
);
-- No index on (stat, value): the ranking tabs read the few players of a world through the primary
-- key, and a second index would slow down every upsert in the battle path.

-- One row per ranked player and day, written by the nightly snapshot for the day that just ended
-- (the world's time zone). `stats` is a copy of the player's player_stat rows then
-- ({"ATTACKS_WON": 3, ...}), so the difference of two days is what he did that day.
create table player_day (
    player_id bigint  not null references player (id) on delete cascade,
    day       date    not null,
    points    integer not null,
    villages  integer not null,
    rank      integer not null,
    stats     jsonb   not null,
    primary key (player_id, day)
);

-- The whole world per day, written with player_day.
create table world_day (
    day                date    primary key,
    players            integer not null,
    villages           integer not null,
    barbarian_villages integer not null,
    points             bigint  not null
);

-- Since when the statistics count: the moment this migration ran (the update to 1.5 on an
-- existing world, the setup of a new one).
create table statistics_since (
    id    smallint    primary key check (id = 1),
    since timestamptz not null
);

insert into statistics_since (id, since) values (1, now());
