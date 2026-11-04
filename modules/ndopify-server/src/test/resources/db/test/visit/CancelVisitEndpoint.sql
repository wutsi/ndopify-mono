-- 1: REQUESTED   2: CONFIRMED   3: CHECKED_IN   4: COMPLETED   5: CANCELLED   6: agent 30's   9: another tenant
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, code, checked_in_at, cancelled_by, cancelled_at, created_at
) VALUES
    (1, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (2, 1, 2, 1, 102, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 10:00:00', '1234', NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (3, 1, 3, 1, 103, 20, 10, 'CHECKED_IN', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 10:00:00', '2345', '2026-12-01 10:05:00', NULL, NULL, '2026-10-01 10:00:00'),
    (4, 1, 2, 1, 102, 20, 10, 'COMPLETED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 10:00:00', '3456', '2026-12-01 10:05:00', NULL, NULL, '2026-10-01 10:00:00'),
    (5, 1, 3, 1, 103, 20, 10, 'CANCELLED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 10:00:00', '4567', NULL, 'CUSTOMER', '2026-10-02 10:00:00', '2026-10-01 10:00:00'),
    (6, 1, 6, 4, 109, 30, 10, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (9, 2, 9, 9, 901, 90, 90, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, NULL, NULL, NULL, '2026-10-01 10:00:00');
