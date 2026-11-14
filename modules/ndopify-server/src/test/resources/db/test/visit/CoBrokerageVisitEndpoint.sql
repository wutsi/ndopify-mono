-- The co-brokerage match: agent 30's search (mandate 4) on agent 20's listing 101
INSERT INTO T_MATCH(
    id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price,
    neighborhood_score, price_score, rooms_score, amenities_score, availability_score, furniture_score,
    created_at, modified_at, co_brokerage
) VALUES
    (50, 1, 4, 101, 30, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
     '2026-10-01 10:00:00', '2026-10-01 10:00:00', TRUE);

-- 1, 2: another agent's visits on listing 101 (CONFIRMED, CHECKED_IN: shown)
-- 3: REQUESTED (hidden)  4: COMPLETED (hidden)  5: own visit (hidden)
-- 6: another listing (hidden)  9: another tenant
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, checked_in_at, code, created_at
) VALUES
    (1, 1, 50, 4, 101, 30, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-02 10:00:00', NULL, '1234', '2026-10-01 10:00:00'),
    (2, 1, 50, 4, 101, 30, 10, 'CHECKED_IN', 'VIDEO',
     '2026-10-01 10:00:00', '2026-12-01 15:00:00', '2026-12-01 15:05:00', '5678', '2026-10-01 10:00:00'),
    (3, 1, 50, 4, 101, 30, 10, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (4, 1, 50, 4, 101, 30, 10, 'COMPLETED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-11-01 10:00:00', '2026-11-01 10:05:00', '9012', '2026-10-01 10:00:00'),
    (5, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-03 10:00:00', NULL, '3456', '2026-10-01 10:00:00'),
    (6, 1, 50, 4, 102, 30, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-04 10:00:00', NULL, '7890', '2026-10-01 10:00:00'),
    (9, 2, 9, 9, 901, 90, 90, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-05 10:00:00', NULL, '1111', '2026-10-01 10:00:00');
