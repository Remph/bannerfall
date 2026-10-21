-- 1.6 lord shipments (09-npc-lords §5.5): NPC lords send what would overflow by merchant to their
-- own villages that still build. On by default, existing worlds included (it only moves resources
-- that would otherwise be wasted).
alter table world_settings add column npc_shipments_enabled boolean not null default true;
