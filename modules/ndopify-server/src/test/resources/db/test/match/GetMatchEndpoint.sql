-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde'),
    (1001, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos');

-- 100: search customer (user 13)   101: owner, a customer of its own (user 14)   200: agent 20 (user 11, the caller)   300: agent 30 (user 12)   900: other tenant
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'owner@gmail.com', 'Owen', 'Er'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (14, 'owner@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false),
    (13, 1, 13, 100, false),
    (14, 1, 14, 101, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101),
    (90, 2, 900);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201),
    (90, 2, 900, 2370201);

-- Searches: 1: agent 20's   2: agent 30's   3: agent 20's, closed   9: other tenant   10: shortlisted (search 3)
-- Listings' mandates: 11..14: agent 20's   19: agent 30's   99: other tenant   10: shortlisted (search 3)
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, rooms, price, price_periodicity_type, currency_code
) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (2, 1, 10, 30, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (3, 1, 10, 20, 'CLOSED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (9, 2, 90, 90, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (14, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (19, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (99, 2, 90, 90, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES
    (1, 1001);

-- 101, 102, 104: published   103: rented   109: agent 30's   901: other tenant
INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, rooms, price, currency_code, price_periodicity_type, title
) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Nice apartment'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 140000, 'XAF', 'MONTHLY', 'Small apartment'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'RENTED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Rented apartment'),
    (104, 1, 14, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Dismissed apartment'),
    (109, 1, 19, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Agent 30 apartment'),
    (901, 2, 99, 90, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Other tenant');

-- 7: proposed, 8: rejected (search 3)   1, 2: active   3: active, listing rented   4: dismissed   5: agent 30's   6: closed search   9: other tenant   10: shortlisted (search 3)
INSERT INTO T_MATCH(
    id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price,
    neighborhood_score, price_score, rooms_score, amenities_score, availability_score, furniture_score,
    created_at, modified_at
) VALUES
    (1, 1, 1, 101, 20, 'ACTIVE', 0.9, 160000, 1.0, 1.0, 0.7, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (2, 1, 1, 102, 20, 'ACTIVE', 0.7, 140000, 0.5, 1.0, 0.7, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (3, 1, 1, 103, 20, 'ACTIVE', 0.95, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (4, 1, 1, 104, 20, 'DISMISSED', 0.8, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (5, 1, 2, 109, 30, 'ACTIVE', 0.8, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (6, 1, 3, 101, 20, 'ACTIVE', 0.8, 160000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (7, 1, 3, 102, 20, 'PROPOSED', 0.8, 140000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (8, 1, 3, 103, 20, 'REJECTED', 0.8, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (9, 2, 9, 901, 90, 'ACTIVE', 0.8, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (10, 1, 3, 104, 20, 'SHORTLISTED', 0.8, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00');
