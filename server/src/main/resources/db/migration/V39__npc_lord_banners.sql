-- 1.4 lord banners (09-npc-lords §14): NPC lords find, fly, fuse and lose banners and standards
-- like players while this is on (and banners are on). Off by default: lords behave as before.
alter table world_settings add column npc_banners_enabled boolean not null default false;
