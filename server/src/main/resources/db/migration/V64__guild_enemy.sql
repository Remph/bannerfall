-- 1.7: the enemies a guild's leader has marked (docs/plan/02-game-rules.md §16.3). A target is a
-- guild, a player (also an NPC lord) or one village; the id points at that table without a foreign
-- key, since a mark only counts while its target exists and is not a guild mate (the list reads it
-- through joins). The marks of a guild end with it.

create table guild_enemy (
    guild_id   bigint       not null references guild (id) on delete cascade,
    kind       varchar(10)  not null check (kind in ('GUILD', 'PLAYER', 'VILLAGE')),
    target_id  bigint       not null,
    created_at timestamptz  not null,
    primary key (guild_id, kind, target_id)
);
