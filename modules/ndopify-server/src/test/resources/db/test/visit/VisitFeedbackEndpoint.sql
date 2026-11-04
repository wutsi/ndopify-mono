-- 1: COMPLETED   2: COMPLETED with feedback   3: CONFIRMED   4: another customer's COMPLETED   9: another tenant
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, code, checked_in_at, checked_out_at, interest, feedback_reason_code,
    feedback_comment, created_at
) VALUES
    (1, 1, 1, 1, 101, 20, 10, 'COMPLETED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 10:00:00', '1234', '2026-12-01 10:05:00', '2026-12-01 10:30:00',
     NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (2, 1, 2, 1, 102, 20, 10, 'COMPLETED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 11:00:00', '2345', '2026-12-01 11:05:00', '2026-12-01 11:30:00',
     'INTERESTED', NULL, NULL, '2026-10-01 10:00:00'),
    (3, 1, 3, 1, 103, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 12:00:00', '3456', NULL, NULL, NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (4, 1, 5, 2, 101, 20, 11, 'COMPLETED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 13:00:00', '4567', '2026-12-01 13:05:00', '2026-12-01 13:30:00',
     NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (9, 2, 9, 9, 901, 90, 90, 'COMPLETED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 10:00:00', '0000', '2026-12-01 10:05:00', '2026-12-01 10:30:00',
     NULL, NULL, NULL, '2026-10-01 10:00:00');
