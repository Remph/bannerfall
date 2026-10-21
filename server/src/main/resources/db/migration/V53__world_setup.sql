-- Guided world setup (1.6, 02-game-rules §1b): the host finished or skipped the setup wizard, so
-- the pre-game page stops offering it. Only read in the pre-game.
alter table world_settings add column setup_done boolean not null default false;
