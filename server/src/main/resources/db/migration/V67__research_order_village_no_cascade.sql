-- 2.0 (F5): the finished rows of research_order are a player's unit levels (02-game-rules §5.4), but
-- their village_id (where the research was started) had `on delete cascade` (V25), so deleting a
-- village would have deleted the levels with it. Nothing deletes villages today. Like the other
-- tables that point at a village, a delete is now refused while research rows still name it, so
-- whoever adds a village delete has to decide what happens to them instead of losing levels.
alter table research_order drop constraint research_order_village_id_fkey;
alter table research_order
    add constraint research_order_village_id_fkey foreign key (village_id) references village (id);
