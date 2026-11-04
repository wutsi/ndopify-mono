-- 1, 2, 5: customer 10, agent 20   3: customer 11, agent 20   4: customer 10, agent 30   9: another tenant
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, code, cancelled_by, cancellation_reason, cancelled_at, created_at
) VALUES
    (1, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 09:00:00', '1111', NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (2, 1, 2, 1, 102, 20, 10, 'CONFIRMED', 'VIDEO',
     '2026-10-01 10:00:00', '2026-12-01 11:00:00', '2222', NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (3, 1, 5, 2, 101, 20, 11, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-02 10:00:00', '3333', NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (4, 1, 6, 4, 109, 30, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-03 10:00:00', '4444', NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (5, 1, 3, 1, 103, 20, 10, 'CANCELLED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-04 10:00:00', '5555', 'CUSTOMER', 'Empêchement',
     '2026-10-01 12:00:00', '2026-10-01 10:00:00'),
    (9, 2, 9, 9, 901, 90, 90, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 09:00:00', '0000', NULL, NULL, NULL, '2026-10-01 10:00:00');
