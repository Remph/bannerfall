-- 2.1 (I1, 02-game-rules §32): the world chronicle, the story of a world once it has ended.
--
-- `chronicle_event`: a moment the chronicle may tell, written when it happens (conquests, guilds
-- founded and gone, enemy guilds marked, allies joining and leaving, attacks ordered, help requests
-- answered or ignored, the court, the victory clock, the end). `day` is the world's game day it
-- happened on (1 = the first day ÷ speed after the opening, 0 = before it), worked out when it is
-- written, since the game clock moves on a wake and a reopen. `data` is the kind's facts as JSON
-- (names copied, so a renamed or deleted player or guild still reads). `at` is history (real time).
--
-- `chronicle_frame`: who owned which village after `day` days (0 = at the opening), one row per day,
-- written by the first check of the day and once more at the end. `owners` is
-- `[[village_id, player_id or 0], …]` for every village that existed; `players` the owners then,
-- `[{"id","name","npc","guildId","guildName","guildTag","points"}, …]`.
create table chronicle_event (
    id    bigserial   primary key,
    at    timestamptz not null,
    day   int         not null,
    kind  text        not null,
    data  jsonb       not null default '{}'
);

create index chronicle_event_at on chronicle_event (at, id);

create table chronicle_frame (
    day      int         primary key,
    at       timestamptz not null,
    owners   jsonb       not null,
    players  jsonb       not null
);

-- A world that ran before I1 keeps its conquests in the conquerors' CONQUEST reports (a report the
-- player deleted, and those of NPC lords older than 7 days, are gone): copied as far as they go.
insert into chronicle_event (at, day, kind, data)
select r.happened_at,
       case
           when w.initialized_at is null or r.happened_at < w.initialized_at then 0
           else floor(extract(epoch from (r.happened_at - w.initialized_at)) * w.speed / 86400)::int + 1
       end,
       'CONQUEST',
       jsonb_build_object(
           'village', jsonb_build_object(
               'id', (r.payload -> 'village' ->> 'id')::bigint,
               'name', r.payload -> 'village' ->> 'name', -- the name it was taken under, as written live
               'x', (r.payload -> 'village' ->> 'x')::int,
               'y', (r.payload -> 'village' ->> 'y')::int),
           'keep', coalesce(v.keep, false),
           'from', case when r.payload -> 'village' ->> 'playerId' is null then null
                        else jsonb_build_object(
                            'id', (r.payload -> 'village' ->> 'playerId')::bigint,
                            'name', r.payload -> 'village' ->> 'playerName',
                            'npc', coalesce(old.npc, false)) end,
           'to', jsonb_build_object('id', p.id, 'name', p.name, 'npc', p.npc))
from report r
join player p on p.id = r.player_id
cross join world_settings w
left join village v on v.id = (r.payload -> 'village' ->> 'id')::bigint
left join player old on old.id = (r.payload -> 'village' ->> 'playerId')::bigint
where r.kind = 'CONQUEST' and r.shared_by_id is null and r.view_of_id is null
order by r.happened_at, r.id;
