-- M31: the paladin (docs/plan/02-game-rules.md §5.5). A new building (the statue, recruits the
-- paladin) and a new unit, plus two world settings. Defaults: one extra slot per 4 villages, at most
-- 5 paladins (0 = no paladin in this world). A running world gets the paladin when it is updated.
alter table world_settings add column paladin_villages_per_slot int not null default 4;
alter table world_settings add column paladin_max_slots int not null default 5;

alter table village add column b_statue smallint not null default 0;

alter table unit_stack add column u_paladin int not null default 0;
alter table movement add column u_paladin int not null default 0;
alter table farm_target add column u_paladin int not null default 0;
