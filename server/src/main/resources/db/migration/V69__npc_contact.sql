-- 2.1 (L1, 09-npc-lords §16.7): first contact between a human and an NPC lord. Written once per pair
-- when the first report between the two is created (an attack or scouting either way, a support from
-- or to the lord); from then on the human sees the lord's variant on its player page. `since` is the
-- game time of that report. Deleting the report later changes nothing; the rows go with either
-- player. Existing worlds start without contacts: their lords have no variant to show.
create table npc_contact (
    player_id bigint      not null references player (id) on delete cascade,
    lord_id   bigint      not null references player (id) on delete cascade,
    since     timestamptz not null,
    primary key (player_id, lord_id)
);
