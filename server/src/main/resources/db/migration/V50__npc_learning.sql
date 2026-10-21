-- Learning NPC lords (1.6, 09-npc-lords §15): the world switch, off by default.
alter table world_settings add column npc_learning_enabled boolean not null default false;

-- The human wins a learning lord reads each round (09 §15.2): attack reports of the last days.
create index report_attack_happened_at_idx on report (happened_at)
    where kind = 'ATTACK' and shared_by_id is null;
