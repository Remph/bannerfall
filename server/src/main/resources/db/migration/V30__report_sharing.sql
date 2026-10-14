-- M40: a player sends one of his reports to other players (docs/plan/02-game-rules.md §14). Each
-- recipient gets a copy in his own list: the same kind, time and payload, plus who sent it and
-- whose view it shows (the payload says "you" for that player). Plain ids and name snapshots,
-- like the village refs in the payload: a copy stays readable whatever happens to either player.

alter table report
    add column shared_by_id   bigint,       -- the sender; null for a report the game created
    add column shared_by_name varchar(64),
    add column view_of_id     bigint,       -- whose report it was first (a copy sent on keeps it)
    add column view_of_name   varchar(64);

-- The unread copies one player sent another (the limit of 30, as for private messages).
create index report_shared_idx on report (player_id, shared_by_id) where shared_by_id is not null;
