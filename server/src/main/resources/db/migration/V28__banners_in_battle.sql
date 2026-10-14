-- M32b: banners in battle (docs/plan/02-game-rules.md §21.2, §21.5–§21.7). The banner thief is a new
-- unit, a command keeps the War, Plunder and Haste numbers of its source village from the moment it
-- left, and the drop rolls are counted per winner and loser.
alter table unit_stack add column u_banner_thief int not null default 0;
alter table movement add column u_banner_thief int not null default 0;
alter table farm_target add column u_banner_thief int not null default 0;

-- Snapshots taken when an ATTACK or SUPPORT leaves (a RETURN keeps those of its trip): War (attack
-- strength +), Plunder (carry capacity +) and Haste (travel time −) as fractions, 0 = no banner.
alter table movement add column banner_attack double precision not null default 0;
alter table movement add column banner_carry double precision not null default 0;
alter table movement add column banner_haste double precision not null default 0;

-- The last drop roll of a winner against a loser (§21.7: one roll per pair per 24 real hours).
-- loser_key is 'P<player id>' for a player or an NPC lord, 'V<village id>' for a barbarian village.
create table banner_roll (
    winner_id bigint      not null references player (id) on delete cascade,
    loser_key varchar(24) not null,
    rolled_at timestamptz not null,
    primary key (winner_id, loser_key)
);
