-- NPC lords form guilds (09-npc-lords §13): the host's switch, the most lords per guild, and the
-- lords the admin placed (the automatic forming leaves them alone).
alter table world_settings add column npc_guilds_enabled boolean not null default false;
alter table world_settings add column npc_guild_max_members integer not null default 3;
alter table npc_brain add column guild_by_admin boolean not null default false;
