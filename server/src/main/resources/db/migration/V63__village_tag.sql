-- 1.7: a player's own tag on one of his villages (docs/plan/02-game-rules.md §26): 1-5 capital
-- letters or digits, read by nobody else. The tag belongs to the owner: a conquest deletes it, and a
-- row whose player is no longer the village's owner is never read.

create table village_tag (
    village_id bigint      primary key references village (id) on delete cascade,
    player_id  bigint      not null references player (id) on delete cascade,
    tag        varchar(5)  not null
);

create index village_tag_player_idx on village_tag (player_id);
