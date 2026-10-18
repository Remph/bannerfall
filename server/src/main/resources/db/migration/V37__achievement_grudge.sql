-- Avenger (1.4, 02-game-rules §19.1): the last time a player (or NPC lord) won an attack of at
-- least 10 units on one of a human player's villages. One row per pair, overwritten by the next win.
create table achievement_grudge (
    player_id bigint      not null references player (id) on delete cascade,
    foe_id    bigint      not null references player (id) on delete cascade,
    beaten_at timestamptz not null,
    primary key (player_id, foe_id)
);
