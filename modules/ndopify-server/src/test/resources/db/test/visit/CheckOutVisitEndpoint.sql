-- 1: checked in 15 min ago   2: checked in 5 min ago   3: not checked in   4: agent 30's   9: another tenant
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, checked_in_at, code, created_at
) VALUES
    (1, 1, 1, 1, 101, 20, 10, 'CHECKED_IN', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 45 MINUTE),
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 15 MINUTE), '1234', '2026-10-01 10:00:00'),
    (2, 1, 2, 1, 102, 20, 10, 'CHECKED_IN', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 35 MINUTE),
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 MINUTE), '5678', '2026-10-01 10:00:00'),
    (3, 1, 3, 1, 103, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 MINUTE), NULL, '1111', '2026-10-01 10:00:00'),
    (4, 1, 6, 4, 109, 30, 10, 'CHECKED_IN', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 45 MINUTE),
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 15 MINUTE), '3333', '2026-10-01 10:00:00'),
    (9, 2, 9, 9, 901, 90, 90, 'CHECKED_IN', 'ON_SITE',
     '2026-10-01 10:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 45 MINUTE),
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 15 MINUTE), '0000', '2026-10-01 10:00:00');
