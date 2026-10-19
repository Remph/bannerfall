-- 1.5 super-accounts 3 (01-architecture §4.9b): what this world last told the account server about
-- a linked player's career. One row per account; the reporter sends a player again only when the
-- hash of his report changed. Gone with the account (never deleted while the world runs).
create table career_sync (
    account_id bigint      primary key references account (id) on delete cascade,
    sent_hash  varchar(64) not null,
    sent_at    timestamptz not null
);
