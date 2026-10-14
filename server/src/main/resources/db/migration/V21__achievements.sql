-- Achievements (honours without any game effect). Rules: docs/plan/02-game-rules.md §19.
-- All four tables belong to a player; the player row itself stays when the host deletes him (the
-- profile is gone then), so nothing here is ever read for a deleted player.

-- Every level a player has earned. One-time achievements have level 1 only.
create table player_achievement (
    player_id   bigint      not null references player (id) on delete cascade,
    achievement text        not null,
    level       smallint    not null,
    earned_at   timestamptz not null,
    primary key (player_id, achievement, level)
);

-- The running totals of the levelled achievements that count events (wins, defences, loot, units,
-- trade volume). The state-based ones (points, villages, days) are read from the game itself.
create table achievement_counter (
    player_id   bigint not null references player (id) on delete cascade,
    achievement text   not null,
    value       bigint not null,
    primary key (player_id, achievement)
);

-- An achievement the host took away: it can't be earned or counted again by that player.
create table achievement_block (
    player_id   bigint      not null references player (id) on delete cascade,
    achievement text        not null,
    revoked_at  timestamptz not null,
    reason      text        null,
    primary key (player_id, achievement)
);

-- The "once per village and 24 h / speed" filter of counted wins and defences (02 §19): the last
-- time an attacker's win (WIN) or an attack he lost (DEFENCE) against a village was counted.
create table battle_credit (
    attacker_id bigint      not null references player (id) on delete cascade,
    village_id  bigint      not null references village (id) on delete cascade,
    kind        text        not null,
    counted_at  timestamptz not null,
    primary key (attacker_id, village_id, kind)
);

create index battle_credit_village on battle_credit (village_id);
