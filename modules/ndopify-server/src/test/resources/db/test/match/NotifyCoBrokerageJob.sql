-- Agent 20 (tenant 1): forward co-brokerage match 1 to send (listing 101 is agent 30's).
-- Not sent: 2 own inventory (listing 102 is agent 20's own), 3 already notified, 4 SHORTLISTED (interest, not forward).
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde'),
    (1001, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos');

-- 100: search customer   101: listing customer   200: agent 20   300: agent 30
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'owner@gmail.com', 'Owen', 'Er'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201);

-- 1, 2, 3: agent 20's searches. 11 (agent 30) and 12 (agent 20): the listings' mandates.
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, rooms, price, price_periodicity_type, currency_code
) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 250000, 'MONTHLY', 'XAF'),
    (2, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 250000, 'MONTHLY', 'XAF'),
    (3, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 250000, 'MONTHLY', 'XAF'),
    (11, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 90000, 'MONTHLY', 'XAF'),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 100000, 'MONTHLY', 'XAF');

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES
    (1, 1001),
    (2, 1001),
    (3, 1001);

-- 101: agent 30's co-brokerage candidate. 102: agent 20's own inventory.
INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, rooms, price, currency_code, price_periodicity_type, title
) VALUES
    (101, 1, 11, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 90000, 'XAF', 'MONTHLY', 'Co-brokerage'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 100000, 'XAF', 'MONTHLY', 'Own');

INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, notified_at, co_brokerage) VALUES
    (1, 1, 1, 101, 20, 'ACTIVE', 0.8, 90000, NULL, TRUE),                     -- forward
    (2, 1, 1, 102, 20, 'ACTIVE', 0.9, 100000, NULL, FALSE),                   -- own inventory
    (3, 1, 2, 101, 20, 'ACTIVE', 0.8, 90000, '2026-10-01 10:00:00', TRUE),    -- already notified
    (4, 1, 3, 101, 20, 'SHORTLISTED', 0.8, 90000, NULL, TRUE);                -- interest, not a forward match
