-- 1, 2: REQUESTED, customer 10   3: REQUESTED, customer 12 (no WhatsApp)   4: already CONFIRMED
-- 5: agent 30's   9: another tenant
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, code, created_at
) VALUES
    (1, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, '2026-10-01 10:00:00'),
    (2, 1, 2, 1, 102, 20, 10, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, '2026-10-01 10:00:00'),
    (3, 1, 7, 3, 105, 20, 12, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, '2026-10-01 10:00:00'),
    (4, 1, 3, 1, 103, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 10:00:00', '4321', '2026-10-01 10:00:00'),
    (5, 1, 6, 4, 109, 30, 10, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, '2026-10-01 10:00:00'),
    (9, 2, 9, 9, 901, 90, 90, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, '2026-10-01 10:00:00');
