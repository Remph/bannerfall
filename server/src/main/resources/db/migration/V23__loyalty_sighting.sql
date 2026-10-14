-- M21: a village's loyalty is shown to a player only for his own villages and for villages he has
-- attacked (docs/plan/02-game-rules.md §10). This is the last value he saw: written by the battle
-- when at least one of his units came back, read by the map. Per player, not shared with the guild.

create table loyalty_sighting (
    player_id  bigint      not null references player (id) on delete cascade,
    village_id bigint      not null references village (id) on delete cascade,
    loyalty    smallint    not null,                    -- rounded down, after the battle
    seen_at    timestamptz not null,                    -- game time of the battle
    primary key (player_id, village_id)
);

-- On a world that already runs: what the attack reports with surviving nobles told, so players
-- keep the loyalty they have already learned (conquering hits and villages that fell excluded).
insert into loyalty_sighting (player_id, village_id, loyalty, seen_at)
select distinct on (r.player_id, v.id)
       r.player_id, v.id, (r.payload -> 'loyalty' ->> 'after')::smallint, r.happened_at
from report r
join village v on v.id = (r.payload -> 'defender' ->> 'id')::bigint
join player p on p.id = r.player_id and not p.npc
where r.kind = 'ATTACK'
  and jsonb_typeof(r.payload -> 'loyalty') = 'object'
  and (r.payload -> 'loyalty' ->> 'conquered')::boolean = false
order by r.player_id, v.id, r.happened_at desc, r.id desc;
