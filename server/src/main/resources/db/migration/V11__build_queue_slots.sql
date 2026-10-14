-- M13c: longer build queue (docs/plan/02-game-rules.md §4.3). Positions 1 to build_queue_size cost the
-- normal price, the paid slots up to build_queue_max cost more. The surcharge is part of the cost the
-- build order already stores (cost_wood, cost_clay, cost_iron), so a cancel refunds what was paid.

alter table world_settings alter column build_queue_size set default 5;
alter table world_settings add column build_queue_max int not null default 10;
-- The old default was 3; a world that still has it gets the new one.
update world_settings set build_queue_size = 5 where build_queue_size = 3;
alter table world_settings add constraint world_settings_build_queue_max_check
    check (build_queue_max >= build_queue_size);
