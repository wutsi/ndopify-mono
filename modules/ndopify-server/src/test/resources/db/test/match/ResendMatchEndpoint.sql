-- Runs after ProposeMatchEndpoint.sql: adds an accepted match, and one whose listing was rented since
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category,
                      city_id, property_type, rooms, price, price_periodicity_type, currency_code)
VALUES (17, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
       (18, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

-- 107: published   108: rented
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id,
                      neighborhood_id, property_type, rooms, price, currency_code, price_periodicity_type, title, url)
VALUES (107, 1, 17, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
        'Accepted apartment', null),
       (108, 1, 18, 20, 'RESIDENTIAL', 'RENTAL', 'RENTED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
        'Rented since apartment', null);

-- 12: accepted by customer 10   13: proposed on a listing rented since
INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, proposed_at, answered_at) VALUES
    (12, 1, 1, 107, 20, 'ACCEPTED', 0.85, 145000, '2026-10-20 10:00:00', '2026-10-21 10:00:00'),
    (13, 1, 1, 108, 20, 'PROPOSED', 0.85, 150000, '2026-10-20 10:00:00', null);
