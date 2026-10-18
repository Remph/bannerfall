-- 1.4: paladin standards (docs/plan/02-game-rules.md §21.18). A standard is a banner row whose type
-- ends in _STANDARD. A village holds one banner and one standard, so the single "one banner per
-- village" key becomes one key per family.
alter table banner drop constraint banner_equipped_village_id_key;
create unique index banner_village_slot_idx on banner (equipped_village_id)
    where type not like '%\_STANDARD';
create unique index banner_standard_slot_idx on banner (equipped_village_id)
    where type like '%\_STANDARD';

-- The standard an ATTACK with a paladin took along when it left (a RETURN keeps the one of its
-- trip): the banner, its kind, its own tier and the tier it worked at. Null without one. No foreign
-- key: the battle checks that the banner is still there before the defenders' thieves take it.
alter table movement add column standard_banner_id bigint;
alter table movement add column standard_type varchar(16);
alter table movement add column standard_tier smallint;
alter table movement add column standard_effective_tier smallint;

-- The admin setting "Defenders' thieves can take standards" (§21.18), on by default.
alter table world_settings add column standard_theft_enabled boolean not null default true;
