-- 1.7: the guild banner (docs/plan/02-game-rules.md §16.4). A guild's members donate resources into
-- a fund; with enough in it any member raises the banner's level, which switches on one small
-- effect for the villages of every member. The row is made with the first donation or the first
-- departure of a member (locked_until: no level-up for 24 h after one). Both tables end with their
-- guild. The donor has no foreign key: a deleted player stays a row of the player table anyway, and
-- the list only reads the donors that are members.

alter table world_settings add column guild_banners_enabled boolean not null default false;

create table guild_banner (
    guild_id     bigint      primary key references guild (id) on delete cascade,
    level        smallint    not null default 0 check (level between 0 and 3),
    fund         bigint      not null default 0 check (fund >= 0),
    locked_until timestamptz
);

create table guild_banner_donation (
    guild_id  bigint not null references guild (id) on delete cascade,
    player_id bigint not null,
    amount    bigint not null check (amount > 0),
    primary key (guild_id, player_id)
);
