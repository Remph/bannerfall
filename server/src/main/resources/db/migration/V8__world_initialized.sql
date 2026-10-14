-- M10: the world's content (initial barbarian villages, from M12 also the NPC lords) is created once,
-- at the first start of the server. Null = not yet. See WorldInitializer, docs/plan/03-data-model.md.

alter table world_settings add column initialized_at timestamptz null;
