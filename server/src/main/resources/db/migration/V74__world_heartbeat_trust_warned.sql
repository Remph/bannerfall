-- 2.1 feature review, finding 01: a solo world must not count the time its server was stopped or
-- suspended as game time (docs/plan/12-solo-and-allies.md §3, 02-game-rules §28).
-- heartbeat_at: the last time the sleep tick (every 10 s) saw an open solo world awake; null while it
-- sleeps or isn't an open solo world. Real time, never moved by FrozenTime. When the server comes back
-- after a stop or suspend (the heartbeat more than 30 s old), the world falls asleep at the heartbeat,
-- so the wake moves every game clock on by the time the server stood still.

alter table world_settings add column heartbeat_at timestamptz;

-- Finding 02: a lord warns only a human whose trust once stood high (the inviter at its join, or
-- above warn-again-above later), never a new guild mate it has never dealt with: a new pair starts
-- as warned (NpcTrust writes it explicitly too).
alter table npc_trust alter column warned set default true;
