-- M14a: a player deleted by the admin keeps his account row (reports and chat messages point at the
-- player, the player at the account) but can no longer log in or found a new village.
alter table account add column disabled boolean not null default false;
