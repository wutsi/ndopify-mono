-- 1, 2, 6, 7: agent 20's, in the check-in window (7: too early)   3: too late   4: cancelled   5: agent 30's
-- 9: another tenant
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, code, code_attempts, created_at
) VALUES
    (1, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', UTC_TIMESTAMP(), '1234', 0, '2026-10-01 10:00:00'),
    (2, 1, 2, 1, 102, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', UTC_TIMESTAMP(), '5678', 0, '2026-10-01 10:00:00'),
    (3, 1, 3, 1, 103, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 HOUR), '1111', 0, '2026-10-01 10:00:00'),
    (4, 1, 1, 1, 101, 20, 10, 'CANCELLED', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR), '2222', 0, '2026-10-01 10:00:00'),
    (5, 1, 6, 4, 109, 30, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', UTC_TIMESTAMP(), '3333', 0, '2026-10-01 10:00:00'),
    (6, 1, 2, 1, 102, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', UTC_TIMESTAMP(), '4444', 4, '2026-10-01 10:00:00'),
    (7, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_ADD(UTC_TIMESTAMP(), INTERVAL 2 HOUR), '5555', 0, '2026-10-01 10:00:00'),
    (9, 2, 9, 9, 901, 90, 90, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', UTC_TIMESTAMP(), '0000', 0, '2026-10-01 10:00:00');
