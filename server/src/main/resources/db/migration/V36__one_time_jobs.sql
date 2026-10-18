-- Jobs that run once at a server start after an update and must never run again (1.4: the paladin
-- moves into the Banner Hall, 02-game-rules §5.5). A job claims its row in the transaction that
-- does the work, so a failed run is retried at the next start.
create table one_time_job (
    name    varchar(64) primary key,
    done_at timestamptz not null
);
