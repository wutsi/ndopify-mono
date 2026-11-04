-- The world a visit lives in: one tenant, a customer, its agent, the agent's other customer, the matched search and
-- listing. Tests add their own visits.

INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, time_zone, date_format, time_format, logo_url, partner_central_url, public_portal_url)
VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'Africa/Douala', 'dd/MM/yyyy', 'HH:mm',
     'https://cdn.ndopify.com/logo.png', 'https://partner.ndopify.com', 'https://www.ndopify.com'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', 'Africa/Douala', 'dd/MM/yyyy', 'HH:mm', NULL, NULL, NULL);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde'),
    (1001, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos');

-- 100: the search's customer (user 13)   200: agent 20 (user 11, the default caller)   300: agent 30 (user 12)
-- 400: another customer (user 15)         500: a customer without WhatsApp (user 16)     900: another tenant
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (400, 1, 'alice@gmail.com', 'Alice', 'Martin'),
    (500, 1, 'bob@gmail.com', 'Bob', 'Durand'),
    (600, 1, 'no.role@gmail.com', 'No', 'Role'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (15, 'alice@gmail.com', false, '2024-06-10 00:00:00'),
    (16, 'bob@gmail.com', false, '2024-06-10 00:00:00'),
    (17, 'no.role@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false),
    (13, 1, 13, 100, false),
    (15, 1, 15, 400, false),
    (16, 1, 16, 500, false),
    (17, 1, 17, 600, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id, whatsapp_number) VALUES
    (10, 1, 100, '+237600000001'),
    (11, 1, 400, '+237600000002'),
    (12, 1, 500, NULL),
    (90, 2, 900, '+237600000009');

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id, whatsapp_number) VALUES
    (20, 1, 200, 2370201, '+237699000001'),
    (30, 1, 300, 2370201, '+237699000002'),
    (90, 2, 900, 2370201, '+237699000009');

-- Searches: 1, 2, 3: agent 20's   4: agent 30's   9: another tenant
-- Listings' mandates: 11..15: agent 20's   19: agent 30's   99: another tenant
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, rooms, price, price_periodicity_type, currency_code
) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (2, 1, 11, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (3, 1, 12, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (4, 1, 10, 30, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (9, 2, 90, 90, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (14, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (15, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (19, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (99, 2, 90, 90, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

-- 101, 102, 103, 104, 105: agent 20's   109: agent 30's   901: another tenant
-- The positions are in Douala: about 50 m apart for 101 / 102, 800 m for 103
INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, rooms, price, currency_code, price_periodicity_type, title, landmark, access_directions,
    latitude, longitude
) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Nice apartment', 'Rue 1.234, Bonapriso', 'Derrière la pharmacie', 4.051100, 9.767900),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 140000, 'XAF', 'MONTHLY',
     'Small apartment', 'Rue 1.235, Bonapriso', 'En face du marché', 4.051550, 9.767900),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Big house', 'Rue 1.236, Bonapriso', 'Au bout de la rue', 4.058300, 9.767900),
    (104, 1, 14, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Fourth apartment', NULL, NULL, NULL, NULL),
    (105, 1, 15, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Fifth apartment', NULL, NULL, NULL, NULL),
    (109, 1, 19, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Agent 30 apartment', 'Rue 2.000', 'Près de la station', 4.051100, 9.767900),
    (901, 2, 99, 90, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Other tenant apartment', NULL, NULL, NULL, NULL);

-- 1, 2, 3, 5, 7: ACCEPTED (a visit may be requested)   4: PROPOSED   6, 8: another agent's   9: another tenant
INSERT INTO T_MATCH(
    id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price,
    neighborhood_score, price_score, rooms_score, amenities_score, availability_score, furniture_score,
    created_at, modified_at
) VALUES
    (1, 1, 1, 101, 20, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (2, 1, 1, 102, 20, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (3, 1, 1, 103, 20, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (4, 1, 1, 104, 20, 'PROPOSED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (5, 1, 2, 101, 20, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (6, 1, 4, 109, 30, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (7, 1, 3, 105, 20, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (8, 1, 1, 109, 30, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (9, 2, 9, 901, 90, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00');
