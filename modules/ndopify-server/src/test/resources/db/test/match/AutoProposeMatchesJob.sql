INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, public_portal_url, currency_code, monetary_format, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'https://www.ndopify.com', 'XAF', '#,###,##0 FCFA', true),
    (2, 'Inactive', 'inactive.localhost', 'fr-CM, en-CM', 'CM', NULL, 'XAF', '#,###,##0 FCFA', false);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name, latitude, longitude) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde', 3.8667, 11.5167);

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'owner@gmail.com', 'Owen', 'Er'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (120, 2, 'other.customer@gmail.com', 'Oth', 'Er'),
    (220, 2, 'other.agent@gmail.com', 'Oth', 'Agent');

-- The customer is already a member: the two proposal e-mails are sent concurrently and must not race on user creation
INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (13, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (13, 1, 13, 100, false);

-- The customer's public-portal access, so the two concurrent proposal e-mails need no write on it
INSERT INTO T_USER_APPLICATION(id, membership_id, application_id) VALUES
    (13, 13, 1);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101),
    (12, 2, 120);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201),
    (22, 2, 220, 2370201);

-- 1, 2: two searches of customer 10, agent 20   3: closed search   11..20: listings' mandates
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id, property_type, rooms, price, price_periodicity_type, currency_code) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (2, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (3, 1, 10, 20, 'CLOSED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (14, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (15, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (16, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (17, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (18, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (19, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (20, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (99, 2, 12, 22, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

-- 101..107, 109, 111: published   108: rented
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, property_type, rooms, price, currency_code, price_periodicity_type, title) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment A'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment B'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment C'),
    (104, 1, 14, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment D'),
    (105, 1, 15, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment E'),
    (106, 1, 16, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment F'),
    (107, 1, 17, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment G'),
    (108, 1, 18, 20, 'RESIDENTIAL', 'RENTAL', 'RENTED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment H'),
    (109, 1, 19, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment I'),
    (111, 1, 20, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Co-brokerage apartment'),
    (201, 2, 99, 22, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Other tenant');

-- 1, 2, 10, 20: to auto-propose; 3: 23h old; 4: shortlisted; 5: dismissed; 6: already proposed;
-- 7: expired; 8: listing rented; 9: search closed; 31: inactive tenant
INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, co_brokerage, status, score, listing_price, created_at, proposed_at) VALUES
    (1, 1, 1, 101, 20, false, 'ACTIVE', 0.9, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (2, 1, 1, 102, 20, false, 'ACTIVE', 0.8, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (3, 1, 1, 103, 20, false, 'ACTIVE', 0.7, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 23 HOUR), NULL),
    (4, 1, 1, 104, 20, false, 'SHORTLISTED', 0.7, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (5, 1, 1, 105, 20, false, 'DISMISSED', 0.7, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (6, 1, 1, 106, 20, false, 'PROPOSED', 0.7, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR)),
    (7, 1, 1, 107, 20, false, 'EXPIRED', 0.7, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY)),
    (8, 1, 1, 108, 20, false, 'ACTIVE', 0.9, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (9, 1, 3, 109, 20, false, 'ACTIVE', 0.9, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (10, 1, 2, 104, 20, false, 'ACTIVE', 0.9, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (20, 1, 1, 111, 20, true, 'ACTIVE', 0.85, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (31, 2, 99, 201, 22, false, 'ACTIVE', 0.9, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL);
