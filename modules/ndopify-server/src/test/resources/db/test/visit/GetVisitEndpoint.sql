-- 1: ON_SITE, confirmed   2: VIDEO, confirmed   3: requested   4: another customer's   9: another tenant
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, code, created_at, agent_comment
) VALUES
    (1, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 10:00:00', '1234', '2026-10-01 10:00:00', 'Client en retard'),
    (2, 1, 2, 1, 102, 20, 10, 'CONFIRMED', 'VIDEO',
     '2026-10-01 10:00:00', '2026-12-01 11:00:00', '5678', '2026-10-01 10:00:00', NULL),
    (3, 1, 3, 1, 103, 20, 10, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, '2026-10-01 10:00:00', NULL),
    (4, 1, 5, 2, 101, 20, 11, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-02 10:00:00', '9999', '2026-10-01 10:00:00', NULL),
    (9, 2, 9, 9, 901, 90, 90, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-01 10:00:00', '0000', '2026-10-01 10:00:00', NULL);
