-- The same agent's co-brokerage visits on a rented (102) and a sold (103) listing: both still host visits.
UPDATE T_LISTING SET status = 'RENTED' WHERE id = 102;
UPDATE T_LISTING SET status = 'SOLD' WHERE id = 103;

INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, checked_in_at, code, created_at
) VALUES
    (10, 1, 50, 4, 103, 30, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-05 10:00:00', NULL, '4444', '2026-10-01 10:00:00');
