-- v1.3: the language of the names the game gives villages, and when the owner got each village
-- (docs/plan/02-game-rules.md §2, 03-data-model.md).

-- "Ben's village" (EN) or "Dorf von Ben" (DE). Existing worlds keep English; the host switches on
-- the admin page, which renames the default names once.
alter table world_settings add column village_name_language varchar(2) not null default 'EN'
    check (village_name_language in ('EN', 'DE'));

-- When the owner got the village: founded or conquered. The village list in /api/me puts the one
-- held longest first. Null for barbarian villages.
alter table village add column owner_since timestamptz null;

-- Existing villages: the latest conquest of the village (the conqueror's or the previous owner's
-- report; a conquest is the only way a village changes hands), else its founding. A village whose
-- conquest reports were all deleted counts from its founding.
update village v
set owner_since = c.at
from (select (payload -> 'village' ->> 'id')::bigint as village_id, max(happened_at) as at
      from report
      where kind in ('CONQUEST', 'VILLAGE_LOST')
      group by 1) c
where c.village_id = v.id
  and v.player_id is not null;

update village set owner_since = created_at where player_id is not null and owner_since is null;
