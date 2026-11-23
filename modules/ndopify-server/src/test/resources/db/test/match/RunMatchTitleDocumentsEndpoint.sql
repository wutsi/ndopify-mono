-- #256: a land purchase requiring one document, and three land listings declaring none, another, and the required one
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name, latitude, longitude) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde', 3.8667, 11.5167),
    (1001, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos', 3.8700, 11.5000);

-- 100: search customer   101: owner   200: agent 20 (user 11)
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'owner@gmail.com', 'Owen', 'Er'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201);

-- 1: the land purchase, requiring one document. 11..13: the sellers' mandates, declaring none, another, and the required one.
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, price, price_periodicity_type, currency_code, moving_period, title_documents, ownership_documents
) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'SALE', 'RESIDENTIAL', 2370201, 'LAND', 15000000, 'NONE', 'XAF', 'WITHIN_3_MONTHS', 'LAND_TITLE', NULL),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'SALE', 'RESIDENTIAL', 2370201, 'LAND', 12000000, 'NONE', 'XAF', NULL, NULL, ''),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'SALE', 'RESIDENTIAL', 2370201, 'LAND', 12000000, 'NONE', 'XAF', NULL, NULL, 'SURVEY_PLAN'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'SALE', 'RESIDENTIAL', 2370201, 'LAND', 12000000, 'NONE', 'XAF', NULL, NULL, 'LAND_TITLE,CERTIFICATE_OF_OWNERSHIP');

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES (1, 1001);

-- A: 101 (declares no document)   B: 102 (declares another one)   C: 103 (declares the required one)
INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, price, currency_code, price_periodicity_type, available_from, title
) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'SALE', 'PUBLISHED', 2370201, 1001, 'LAND', 12000000, 'XAF', 'NONE', NULL, 'A'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'SALE', 'PUBLISHED', 2370201, 1001, 'LAND', 12000000, 'XAF', 'NONE', NULL, 'B'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'SALE', 'PUBLISHED', 2370201, 1001, 'LAND', 12000000, 'XAF', 'NONE', NULL, 'C');
