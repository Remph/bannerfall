-- M12b: NPC lords (docs/plan/09-npc-lords.md, 03-data-model.md): the brain of each lord, the NPC
-- settings of the world and the production factor of a player.

alter table world_settings add column npc_lord_count    int         not null default 4;
alter table world_settings add column npc_difficulty    varchar(8)  not null default 'NORMAL';
alter table world_settings add column npc_think_minutes int         not null default 20;
-- The CUSTOM difficulty's values (09 §8); null until the admin first picks CUSTOM (NORMAL until then).
alter table world_settings add column npc_custom        jsonb       null;

-- Multiplies the production of all villages of the player: 1.0 for humans, set by NpcBalance for NPC lords.
alter table player add column production_factor double precision not null default 1.0;

create table npc_brain (
    player_id     bigint      primary key references player (id) on delete cascade,
    personality   varchar(16) not null,
    template      varchar(32) not null,
    last_think_at timestamptz null,
    memory        jsonb       not null default '{}',
    version       bigint      not null default 0
);
