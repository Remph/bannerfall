-- 1.4: a player's own note on a village (docs/plan/02-game-rules.md §23). Only its author reads
-- it: the map and the farm list show it. It belongs to the player and the village, so it stays when
-- the village changes owner.

create table village_note (
    player_id  bigint        not null references player (id) on delete cascade,
    village_id bigint        not null references village (id) on delete cascade,
    text       varchar(500)  not null,                  -- trimmed, 1-500 characters
    updated_at timestamptz   not null,
    primary key (player_id, village_id)
);
