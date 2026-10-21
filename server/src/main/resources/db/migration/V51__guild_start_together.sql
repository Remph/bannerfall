-- 1.6 (02-game-rules §2): guild mates start next to each other at the opening and on a respawn.
-- On by default: it only changes anything for players who are in a guild before they get a village.
alter table world_settings add column guild_start_together boolean not null default true;
