-- The co-brokerage match: agent 30's search (mandate 4) on agent 20's listing 101
INSERT INTO T_MATCH(
    id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price,
    neighborhood_score, price_score, rooms_score, amenities_score, availability_score, furniture_score,
    created_at, modified_at, co_brokerage
) VALUES
    (50, 1, 4, 101, 30, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
     '2026-10-01 10:00:00', '2026-10-01 10:00:00', TRUE);

-- 17: agent 30's search, for the two filtered visits below (not mandate 4, so the other visit tests stay untouched)
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, rooms, price, price_periodicity_type, currency_code
) VALUES
    (17, 1, 10, 30, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

-- 106: agent 20's DRAFT listing (a draft must not reach the block), on its own LISTING mandate 16
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, rooms, price, price_periodicity_type, currency_code
) VALUES
    (16, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, rooms, price, currency_code, price_periodicity_type, title
) VALUES
    (106, 1, 16, 20, 'RESIDENTIAL', 'RENTAL', 'DRAFT', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Draft apartment');

-- 1, 2: another agent's visits on listing 101 (CONFIRMED, CHECKED_IN: shown)
-- 3: REQUESTED (hidden)  4: COMPLETED (hidden)  5: own visit (hidden)
-- 6: another listing (hidden)  7: agent 30's own listing 109 (not agent 20's, hidden)
-- 8: agent 20's DRAFT listing 106 (not a live listing, hidden)  9: another tenant
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
    (7, 1, 50, 17, 109, 30, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-06 10:00:00', NULL, '2222', '2026-10-01 10:00:00'),
    (8, 1, 50, 17, 106, 30, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-07 10:00:00', NULL, '3333', '2026-10-01 10:00:00'),
    (9, 2, 9, 9, 901, 90, 90, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-05 10:00:00', NULL, '1111', '2026-10-01 10:00:00');
