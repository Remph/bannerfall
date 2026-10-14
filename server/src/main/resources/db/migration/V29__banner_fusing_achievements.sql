-- M32c: fusing and the banner achievements (docs/plan/02-game-rules.md §21.4.2, §21.8). A line of the
-- banner log now names the banner's type and tier, so the log still knows them after the banner
-- was fused or destroyed: Full Set ("has owned all nine types") reads them from there.
alter table banner_event add column type varchar(16);
alter table banner_event add column tier smallint;

-- the lines written before V29: from the banner, where it still exists
update banner_event e
set type = b.type, tier = b.tier
from banner b
where b.id = e.banner_id;

-- the achievements look up "lines that gave a player a banner"
create index banner_event_to_player_idx on banner_event (to_player_id, banner_id);
