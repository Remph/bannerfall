-- 2.1 (S4, 12-solo-and-allies §9): an allied NPC lord asks a human of its guild for help.
-- `kind`: SUPPORT (an attack it can't hold is on the way to `village_id`: `defenders` wanted there
-- before `deadline`, the attack's arrival; `attack_id` is that movement, no foreign key since a
-- movement is deleted when it lands) or RESOURCES (a shortage has held its next step, a noble or the
-- wall, for 12 hours: `wood`, `clay`, `iron` wanted at `village_id` before `deadline`; `delivered_*`
-- add up what the human's merchants brought since). `goal`: NOBLE or WALL for RESOURCES.
-- `state`: OPEN, ANSWERED (in time: +15 trust), DECLINED (-5), IGNORED (deadline passed, -10), LATE
-- (the human answered, but not in time: nothing) or CANCELLED (the attack turned back, the lord left
-- the guild or lost the village: nothing). One open request per lord. `thanks_due`: answered in time,
-- the lord's next round sends its thanks (and, once a day, a shipment, `thanked_at`).
-- `supported`: a support of the human arrived at `village_id` after the request and by its
-- deadline; only then does his support there answer it (one he left there before does not).
-- `created_at`, `deadline` and `thanked_at` are game time (the daily caps, the deadline) and move
-- with the world's clock (FrozenTime); `closed_at` is history.
create table npc_help_request (
    id              bigserial   primary key,
    lord_id         bigint      not null references player (id) on delete cascade,
    player_id       bigint      not null references player (id) on delete cascade,
    kind            text        not null,
    village_id      bigint      not null references village (id) on delete cascade,
    attack_id       bigint      null,
    defenders       int         not null default 0,
    goal            text        null,
    wood            int         not null default 0,
    clay            int         not null default 0,
    iron            int         not null default 0,
    delivered_wood  int         not null default 0,
    delivered_clay  int         not null default 0,
    delivered_iron  int         not null default 0,
    created_at      timestamptz not null,
    deadline        timestamptz not null,
    state           text        not null,
    closed_at       timestamptz null,
    thanks_due      boolean     not null default false,
    thanked_at      timestamptz null,
    supported       boolean     not null default false
);

create unique index npc_help_request_open on npc_help_request (lord_id) where state = 'OPEN';
create index npc_help_request_player on npc_help_request (player_id, created_at);

-- The warning letter (S4): an ally whose trust in a human falls towards 0 writes once, and again
-- only after its trust has risen well above the mark.
alter table npc_trust add column warned boolean not null default false;
