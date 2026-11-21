-- 2.1 (S3, 12-solo-and-allies §8): a human's order to an allied NPC lord. One order per lord (a new
-- one replaces it). `kind`: ATTACK, SUPPORT, SEND (resources) or HOLD (leave the village alone).
-- `state`: OPEN (waits for the lord's next round), SCOUTING (the lord scouts the target first),
-- PLANNED (a "land together" march leaves at `depart_at` with `army` from `source_village_id`),
-- ACTIVE (carried out: on the way, standing as a support, holding off) or REFUSED (`answer` is the
-- kind of the letter that said why). `answer` is the kind of the lord's last letter about it.
-- All times are game time and move with the world's clock (FrozenTime).
create table npc_order (
    lord_id           bigint      primary key references player (id) on delete cascade,
    player_id         bigint      not null references player (id) on delete cascade,
    kind              text        not null,
    target_village_id bigint      not null references village (id) on delete cascade,
    land_together     boolean     not null default false,
    state             text        not null,
    answer            text        null,
    given_at          timestamptz not null,
    answered_at       timestamptz null,
    source_village_id bigint      null references village (id) on delete set null,
    depart_at         timestamptz null,
    arrive_at         timestamptz null,
    army              jsonb       null,
    catapult_target   text        null,
    scout_tries       int         not null default 0
);

create index npc_order_player on npc_order (player_id);
