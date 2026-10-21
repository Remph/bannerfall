-- 1.6: demolishing a building (02-game-rules §4.5). A demolition is an order of the build queue
-- that takes one level away instead of adding one; it costs nothing, so its cost columns are 0 and
-- target_level is the level the building has once it is done.
alter table build_order add column demolish boolean not null default false;
