-- M20: tribes are called guilds (docs/plan/02-game-rules.md §16). V16 and V17 stay as they were
-- (databases that ran them must still validate), so this migration renames what they created.

alter table tribe rename to guild;
alter table tribe_member rename to guild_member;
alter table tribe_invitation rename to guild_invitation;

alter table guild_member rename column tribe_id to guild_id;
alter table guild_invitation rename column tribe_id to guild_id;
alter table chat_message rename column tribe_id to guild_id;
alter table world_settings rename column tribes_enabled to guilds_enabled;
alter table world_settings rename column tribe_max_members to guild_max_members;

alter index tribe_name_lower rename to guild_name_lower;
alter index tribe_tag_lower rename to guild_tag_lower;
alter index tribe_member_tribe rename to guild_member_guild;
alter index tribe_member_one_leader rename to guild_member_one_leader;
alter index tribe_invitation_player rename to guild_invitation_player;
alter index chat_message_tribe rename to chat_message_guild;

-- The automatically named keys (primary keys, foreign keys, the unique pair of the invitation,
-- the not-null constraints of PostgreSQL 18) and the identity sequences still carry the old word; rename them too so the schema reads clean.
do $$
declare
    r record;
begin
    for r in
        select c.conrelid::regclass::text as tbl, c.conname
        from pg_constraint c
        where c.conrelid in ('guild'::regclass, 'guild_member'::regclass,
                             'guild_invitation'::regclass, 'chat_message'::regclass,
                             'world_settings'::regclass)
          and c.conname like '%tribe%'
    loop
        execute format('alter table %s rename constraint %I to %I',
                       r.tbl, r.conname, replace(r.conname, 'tribe', 'guild'));
    end loop;
    for r in
        select sequencename from pg_sequences
        where schemaname = current_schema() and sequencename like 'tribe%'
    loop
        execute format('alter sequence %I rename to %I',
                       r.sequencename, replace(r.sequencename, 'tribe', 'guild'));
    end loop;
end
$$;

-- Achievements are stored by name.
update player_achievement set achievement = 'GUILD_FOUNDER' where achievement = 'TRIBE_FOUNDER';
update player_achievement set achievement = 'GUILD_MEMBER' where achievement = 'TRIBE_MEMBER';
update achievement_counter set achievement = 'GUILD_FOUNDER' where achievement = 'TRIBE_FOUNDER';
update achievement_counter set achievement = 'GUILD_MEMBER' where achievement = 'TRIBE_MEMBER';
update achievement_block set achievement = 'GUILD_FOUNDER' where achievement = 'TRIBE_FOUNDER';
update achievement_block set achievement = 'GUILD_MEMBER' where achievement = 'TRIBE_MEMBER';
