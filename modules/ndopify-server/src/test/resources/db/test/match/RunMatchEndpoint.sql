-- The worked example of docs/superpowers/plans/2026-10-31-search-listing-matching.md. The availabilities are relative
-- to today, since the server's clock isn't fixed
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

-- 1002 is ~1.5 km north of 1001
INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name, latitude, longitude) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde', 3.8667, 11.5167),
    (1001, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos', 3.8700, 11.5000),
    (1002, 2370201, 'NEIGHBORHOOD', 'CM', 'Etoudi', 'Etoudi', 3.8835, 11.5000);

-- 100: search customer   101: owner   200: agent 20 (user 11)   300: agent 30
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'owner@gmail.com', 'Owen', 'Er'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201);

-- 1: the search. 11..17: the listings' mandates (16: agent 30's, 17: the search customer's own property)
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, rooms, price, price_periodicity_type, currency_code,
    generator_set, secured_fenced, water_tank, moving_period
) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '3', 250000, 'MONTHLY', 'XAF', true, true, true, 'WITHIN_3_MONTHS'),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '3', 240000, 'MONTHLY', 'XAF', false, false, false, null),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '4', 265000, 'MONTHLY', 'XAF', false, false, false, null),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 200000, 'MONTHLY', 'XAF', false, false, false, null),
    (14, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '3', 290000, 'MONTHLY', 'XAF', false, false, false, null),
    (15, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'HOUSE', '3', 240000, 'MONTHLY', 'XAF', false, false, false, null),
    (16, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '3', 240000, 'MONTHLY', 'XAF', false, false, false, null),
    (17, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '3', 240000, 'MONTHLY', 'XAF', false, false, false, null);

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES
    (1, 1001);

-- A: 101   B: 102   C: 103   D: 104 (over budget + 10%)   E: 105 (a house)   106: agent 30's   107: the customer's own
INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, rooms, price, currency_code, price_periodicity_type,
    generator_set, secured_fenced, water_tank, available_from, title
) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 3, 240000, 'XAF', 'MONTHLY', false, true, true, DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'A'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1002, 'APARTMENT', 4, 265000, 'XAF', 'MONTHLY', true, true, true, null, 'B'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 200000, 'XAF', 'MONTHLY', false, true, false, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 3 MONTH), INTERVAL 15 DAY), 'C'),
    (104, 1, 14, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 3, 290000, 'XAF', 'MONTHLY', true, true, true, null, 'D'),
    (105, 1, 15, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'HOUSE', 3, 240000, 'XAF', 'MONTHLY', true, true, true, null, 'E'),
    (106, 1, 16, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 3, 240000, 'XAF', 'MONTHLY', true, true, true, null, 'Agent 30'),
    (107, 1, 17, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 3, 240000, 'XAF', 'MONTHLY', true, true, true, null, 'Own');
