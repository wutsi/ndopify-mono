INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde'),
    (1001, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos');

-- 100: search customer   101: owner   200: agent 20 (user 11, the caller)   300: agent 30
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'owner@gmail.com', 'Owen', 'Er'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201);

-- Seven searches of agent 20. 1, 4, 5, 6, 7: budget below listing 102 (own, 240 000, no own match).
-- 2, 3: budget high enough for 102.
-- Listing mandates: 11, 14, 15: agent 30's   12: agent 20's   13: agent 30's, but the search customer's own property
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, rooms, price, price_periodicity_type, currency_code
) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 100000, 'MONTHLY', 'XAF'),
    (2, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 250000, 'MONTHLY', 'XAF'),
    (3, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 250000, 'MONTHLY', 'XAF'),
    (4, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 100000, 'MONTHLY', 'XAF'),
    (5, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 100000, 'MONTHLY', 'XAF'),
    (6, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 100000, 'MONTHLY', 'XAF'),
    (7, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 100000, 'MONTHLY', 'XAF'),
    (11, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 90000, 'MONTHLY', 'XAF'),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 240000, 'MONTHLY', 'XAF'),
    (13, 1, 10, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 90000, 'MONTHLY', 'XAF'),
    (14, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 90000, 'MONTHLY', 'USD'),
    (15, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 90000, 'MONTHLY', 'XAF');

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES
    (1, 1001),
    (2, 1001),
    (3, 1001),
    (4, 1001),
    (5, 1001),
    (6, 1001),
    (7, 1001);

-- 101: co-brokerage candidate   102: own inventory   103: the customer's own property
-- 104: another currency   105: rented
INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, rooms, price, currency_code, price_periodicity_type, title
) VALUES
    (101, 1, 11, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 90000, 'XAF', 'MONTHLY', 'Co-brokerage'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 240000, 'XAF', 'MONTHLY', 'Own'),
    (103, 1, 13, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 90000, 'XAF', 'MONTHLY', 'Customer own'),
    (104, 1, 14, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 90000, 'USD', 'MONTHLY', 'Other currency'),
    (105, 1, 15, 30, 'RESIDENTIAL', 'RENTAL', 'RENTED', 2370201, 1001, 'APARTMENT', 2, 90000, 'XAF', 'MONTHLY', 'Rented');

INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, co_brokerage) VALUES
    (1, 1, 3, 101, 20, 'ACTIVE', 0.8, 90000, TRUE),      -- sticky
    (2, 1, 4, 102, 20, 'DISMISSED', 0.9, 240000, FALSE), -- does not block the fallback
    (3, 1, 5, 102, 20, 'REJECTED', 0.9, 240000, FALSE),  -- does not block the fallback
    (4, 1, 6, 104, 20, 'ACTIVE', 0.8, 90000, TRUE),      -- no longer qualifies (USD), never deleted
    (5, 1, 7, 105, 20, 'ACTIVE', 0.8, 90000, TRUE);      -- one of search 7's, but its listing is rented
