-- M15b: the tribe chat. Rules: docs/plan/02-game-rules.md §13 and §16.
-- A tribe message is a chat_message with a tribe_id; the global chat reads tribe_id is null.
-- A tribe that ends takes its messages with it.

alter table chat_message add column tribe_id bigint null references tribe (id) on delete cascade;
create index chat_message_tribe on chat_message (tribe_id, id) where tribe_id is not null;
