-- Logging in through an account server (1.5, docs/plan/01-architecture.md §4.9a): an account can
-- belong to an account on an OpenID Connect provider, identified by the provider's issuer and the
-- subject (its account id there). Such an account has no password of its own on this world.
-- Accounts without these columns (every account before 1.5) log in with their password as before.

alter table account
    add column external_issuer  varchar(255) null,
    add column external_subject varchar(255) null,
    alter column password_hash drop not null,
    add constraint account_external_uq unique (external_issuer, external_subject),
    -- both or neither; an account without a password must belong to the account server
    add constraint account_external_pair_ck
        check ((external_issuer is null) = (external_subject is null)),
    add constraint account_login_ck
        check (password_hash is not null or external_subject is not null);
