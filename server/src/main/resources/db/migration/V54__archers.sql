-- 1.6: archers (docs/plan/02-game-rules.md §5.6). The archer (barracks) and the mounted archer
-- (stable) are two new units, recruited only in a world with archers_enabled; off by default, and
-- without archers every battle comes out as before.
alter table world_settings add column archers_enabled boolean not null default false;

alter table unit_stack add column u_archer int not null default 0;
alter table unit_stack add column u_mounted_archer int not null default 0;
alter table movement add column u_archer int not null default 0;
alter table movement add column u_mounted_archer int not null default 0;
alter table farm_target add column u_archer int not null default 0;
alter table farm_target add column u_mounted_archer int not null default 0;
alter table farm_template add column u_archer int not null default 0;
alter table farm_template add column u_mounted_archer int not null default 0;
alter table recruit_template add column u_archer int not null default 0;
alter table recruit_template add column u_mounted_archer int not null default 0;
