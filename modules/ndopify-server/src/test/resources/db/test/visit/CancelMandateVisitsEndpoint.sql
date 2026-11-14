INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, time_zone, date_format, time_format, logo_url, partner_central_url, public_portal_url)
VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'Africa/Douala', 'dd/MM/yyyy', 'HH:mm',
     'https://cdn.ndopify.com/logo.png', 'https://partner.ndopify.com', 'https://www.ndopify.com');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (400, 1, 'owner@gmail.com', 'Secret', 'Owner');

INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (14, 'owner@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false),
    (13, 1, 13, 100, false),
    (14, 1, 14, 400, false);

-- The customer's public-portal access, so the link token of its cancellation e-mail needs no write (no race
-- when the mandate ending cancels several of its visits at once)
INSERT INTO T_USER_APPLICATION(id, membership_id, application_id) VALUES
    (13, 13, 1);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 400);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id, whatsapp_number) VALUES
    (20, 1, 200, 2370201, '+237699000001'),
    (30, 1, 300, 2370201, '+237699000002');

-- 1: A's search (agent 20)   2: another search (agent 20)   11: B's listing mandate (agent 30)
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id, property_type, rooms, price, price_periodicity_type, currency_code)
VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (2, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (11, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id, property_type, rooms, price, currency_code, price_periodicity_type, title, landmark, access_directions, latitude, longitude)
VALUES
    (101, 1, 11, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, NULL, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Villa Bastos', 'Rue 1.234', 'Derrière la pharmacie', 4.051100, 9.767900);

INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, created_at, modified_at, co_brokerage)
VALUES
    (50, 1, 1, 101, 20, 'ACCEPTED', 0.9, 150000, '2026-10-01 10:00:00', '2026-10-01 10:00:00', TRUE);

-- Visit 1: CONFIRMED via A's search   Visit 2: CHECKED_IN   Visit 3: REQUESTED
-- Visit 4: CONFIRMED via search 2, on the same listing (the listing side)
INSERT INTO T_VISIT(id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type, requested_at, scheduled_at, checked_in_at, code, created_at)
VALUES
    (1, 1, 50, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE', '2026-10-01 10:00:00', '2026-12-02 10:00:00', NULL, '1234', '2026-10-01 10:00:00'),
    (2, 1, 50, 1, 101, 20, 10, 'CHECKED_IN', 'ON_SITE', '2026-10-01 10:00:00', '2026-12-01 10:00:00', '2026-12-01 10:05:00', '5678', '2026-10-01 10:00:00'),
    (3, 1, 50, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', '2026-10-01 10:00:00', NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (4, 1, 50, 2, 101, 20, 10, 'CONFIRMED', 'ON_SITE', '2026-10-01 10:00:00', '2026-12-03 10:00:00', NULL, '9012', '2026-10-01 10:00:00');
