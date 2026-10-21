-- 1.5.2: Firefox on Android doesn't say which world key its push subscription was made with, so
-- until 1.5.2 the web made a new subscription at nearly every start and the server kept the old
-- ones (the push service still accepts them, so they never went): one phone counted as ten
-- devices. The web now keeps its subscription and tells the server when its address changes.
-- Once: of a player's devices on the same push service, the newest stays (the browser's current
-- address, so it keeps getting notifications without opening the game) and the older ones go. A
-- second real device on that service signs up again at its next start (pushClient's refreshPush,
-- the same as after a password change). The switches stay.
delete from push_subscription old
using push_subscription newer
where newer.player_id = old.player_id
  and split_part(newer.endpoint, '/', 3) = split_part(old.endpoint, '/', 3)
  and newer.id > old.id;
