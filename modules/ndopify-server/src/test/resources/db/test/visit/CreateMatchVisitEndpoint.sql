-- The booking's edge cases: an accepted match that already has a visit, one whose mandate ended, one whose listing
-- left the market, and a second tenant. VisitFixtures.sql has the rest (accepted matches 1, 2, 3, 5, 7).

-- Match 2 already has an open visit: a second booking is refused.
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, created_at
) VALUES
    (2, 1, 2, 1, 102, 20, 10, 'REQUESTED', 'ON_SITE', '2026-11-19 10:00:00', '2026-11-18 10:00:00');

-- Mandate 21 ended after its match was accepted, and mandate 22's listing is a draft: neither can be visited.
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, rooms, price, price_periodicity_type, currency_code
) VALUES
    (21, 1, 10, 20, 'CANCELLED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (22, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, rooms, price, currency_code, price_periodicity_type, title
) VALUES
    (106, 1, 21, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Closed mandate apartment'),
    (107, 1, 22, 20, 'RESIDENTIAL', 'RENTAL', 'DRAFT', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Draft apartment');

INSERT INTO T_MATCH(
    id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price,
    neighborhood_score, price_score, rooms_score, amenities_score, availability_score, furniture_score,
    created_at, modified_at
) VALUES
    (10, 1, 21, 106, 20, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (11, 1, 22, 107, 20, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00');
