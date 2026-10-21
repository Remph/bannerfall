-- 1.6: the rising noble price (docs/plan/02-game-rules.md §5.6). Every village and noble a player
-- owns beyond the first four makes his next noble noblePriceStep × the base price dearer (at most
-- 7×); 0 is the flat price of before. A running world gets it on at the update as well (decided
-- 2026-10-20). Nothing else changes: the rank is counted from what players own at the moment, and
-- recruit orders keep what they paid.
alter table world_settings add column noble_price_step double precision not null default 0.25
    check (noble_price_step >= 0 and noble_price_step <= 1);
