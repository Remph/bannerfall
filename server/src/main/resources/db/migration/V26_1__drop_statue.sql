-- M31 follow-up: the paladin is recruited in the barracks, the statue is gone again. V26 stays as it
-- was merged (a database that already ran it must still pass Flyway's checksum check); this
-- version 26.1 cleans up after it and sorts between V26 and V27. A statue that is still in a build
-- queue has no building type any more, so its order and completion event are deleted (dev worlds
-- only, the statue never reached a released version; no refund).
delete from game_event
 where kind = 'BUILD_COMPLETE'
   and ref_id in (select id from build_order where building = 'STATUE');
delete from build_order where building = 'STATUE';

alter table village drop column b_statue;
