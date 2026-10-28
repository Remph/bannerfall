-- Chat read state synced across a player's devices (1.7): the newest chat message he has seen, per
-- channel. guild_id null = the world chat, otherwise the chat of that guild (a guild that ends takes
-- its rows with it). Only moves forward (docs/plan/04-api.md, Chat).
create table chat_seen (
    id           bigint generated always as identity primary key,
    player_id    bigint not null references player (id) on delete cascade,
    guild_id     bigint null references guild (id) on delete cascade,
    last_seen_id bigint not null
);
-- one row per player and channel; null counts as 0 here (a unique index treats nulls as distinct)
create unique index chat_seen_channel on chat_seen (player_id, coalesce(guild_id, 0));
