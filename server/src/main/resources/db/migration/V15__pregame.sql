-- M14g: the pre-game state (docs/plan/02-game-rules.md §1b, 08-host-console §2). While pregame is
-- true and the world's content doesn't exist yet (initialized_at is null) players can register,
-- chat and read the world settings, but have no village; the host configures the world and sets
-- opens_at. At opens_at (event WORLD_OPEN) the content is created, every registered player gets a
-- village and pregame goes back to false.
alter table world_settings add column pregame boolean not null default false;
alter table world_settings add column opens_at timestamptz null;
