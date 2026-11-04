-- 1, 6: agent 20's, in the no-show window   2: too early   3: too late   4: cancelled   5: agent 30's
-- 6: VIDEO   9: another tenant
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, code, created_at
) VALUES
    (1, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 MINUTE), '1234', '2026-10-01 10:00:00'),
    (2, 1, 2, 1, 102, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', UTC_TIMESTAMP(), '5678', '2026-10-01 10:00:00'),
    (3, 1, 3, 1, 103, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 HOUR), '1111', '2026-10-01 10:00:00'),
    (4, 1, 1, 1, 101, 20, 10, 'CANCELLED', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 MINUTE), '2222', '2026-10-01 10:00:00'),
    (5, 1, 6, 4, 109, 30, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 MINUTE), '3333', '2026-10-01 10:00:00'),
    (6, 1, 2, 1, 102, 20, 10, 'CONFIRMED', 'VIDEO',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 MINUTE), '4444', '2026-10-01 10:00:00'),
    (9, 2, 9, 9, 901, 90, 90, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 MINUTE), '0000', '2026-10-01 10:00:00');
