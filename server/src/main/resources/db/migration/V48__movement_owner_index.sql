-- 1.5.1: a player's own movements (farm sends, the NPC lords' rounds: findAttacksUnderWay) were read
-- with a scan of the whole table.
create index movement_owner_kind_idx on movement (owner_player_id, kind);
