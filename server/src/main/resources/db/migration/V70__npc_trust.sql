-- 2.1 (S2b, 12-solo-and-allies §5 to §7): trust between NPC lords and humans, letters from lords,
-- and the court of a solo world.

-- Trust of a lord towards a human (-100..100, start 0). `trust` is the value at `trust_at` (game
-- time); it drifts towards 0 by `drift-per-day` a day (÷ speed) from there, so a read works it out
-- without a write. `changes` holds the last three changes for the diplomacy page
-- ([{"reason": "SUPPORT", "delta": 10, "village": "Wulfsburg"}, ...], newest first, no times).
-- `invite_after`: after a refusal the next invitation waits a day (÷ speed). `day_start`,
-- `day_support` and `day_shipped`: the daily caps of support and shipments. All times are game
-- time and move with the world's clock (FrozenTime).
create table npc_trust (
    lord_id      bigint           not null references player (id) on delete cascade,
    player_id    bigint           not null references player (id) on delete cascade,
    trust        double precision not null default 0,
    trust_at     timestamptz      not null,
    invite_after timestamptz      null,
    day_start    timestamptz      null,
    day_support  int              not null default 0,
    day_shipped  bigint           not null default 0,
    changes      jsonb            not null default '[]'::jsonb,
    primary key (lord_id, player_id)
);

create index npc_trust_player on npc_trust (player_id);

-- A human's win against somebody a lord holds a grudge against raises the lord's trust once per
-- grudge: the grudge is told apart by its end (`grudge_until`, game time like the grudge itself).
create table npc_trust_credit (
    lord_id      bigint      not null references player (id) on delete cascade,
    player_id    bigint      not null references player (id) on delete cascade,
    target_id    bigint      not null references player (id) on delete cascade,
    grudge_until timestamptz not null,
    primary key (lord_id, player_id, target_id)
);

-- A letter from an NPC lord (joining, refusing, leaving): a private message whose text the client
-- writes from `letter` ({"kind": "JOINED", "guild": "..."}) in the reader's language; `text` stays
-- empty for a letter.
alter table private_message add column letter jsonb null;

-- The court of a solo world (12 §6): the lord guild with the most points at the last victory check,
-- kept to tell when another one takes its place. No foreign key: `truncate guild cascade` would
-- empty world_settings with it; a court whose guild is gone is simply replaced at the next check.
alter table world_settings add column court_guild_id bigint null;
