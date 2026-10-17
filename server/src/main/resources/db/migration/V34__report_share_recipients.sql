-- 1.3.1: the players someone last sent a report to by name, offered first as quick picks when he
-- sends the next one (docs/plan/02-game-rules.md §14). One row per sender and recipient with the
-- time of the latest send. Kept apart from the copies, so a recipient deleting his copy doesn't
-- change the sender's picks.

create table report_share_recipient (
    sender_id    bigint      not null references player (id) on delete cascade,
    recipient_id bigint      not null references player (id) on delete cascade,
    sent_at      timestamptz not null,
    primary key (sender_id, recipient_id)
);

-- The copies sent before: each sender's recipients with the time of the newest copy.
insert into report_share_recipient (sender_id, recipient_id, sent_at)
select r.shared_by_id, r.player_id, max(r.created_at)
from report r
where r.shared_by_id is not null
  and exists (select 1 from player p where p.id = r.shared_by_id)
group by r.shared_by_id, r.player_id;
