-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde'),
    (1001, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos');

-- 100: search customer   101: owner   200: agent 20 (user 11, the caller)   300: agent 30 (user 12)   900: other tenant
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'owner@gmail.com', 'Owen', 'Er'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101),
    (90, 2, 900);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201),
    (90, 2, 900, 2370201);

-- Searches: 1, 3: agent 20's   2: agent 30's   9: other tenant
-- Listings' mandates: 11..17: agent 20's   19: agent 30's   99: other tenant
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, rooms, price, price_periodicity_type, currency_code
) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (2, 1, 10, 30, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (3, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (9, 2, 90, 90, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (14, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (15, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (16, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (17, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (19, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (99, 2, 90, 90, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, rooms, price, currency_code, price_periodicity_type, title
) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Active apartment'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Proposed apartment'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Accepted apartment'),
    (104, 1, 14, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Expired apartment'),
    (105, 1, 15, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Rejected apartment'),
    (106, 1, 16, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Dismissed apartment'),
    (107, 1, 17, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Shortlisted apartment'),
    (109, 1, 19, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Agent 30 apartment'),
    (901, 2, 99, 90, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Other tenant');

-- Mandate 1: one match per status, so that only ACTIVE, PROPOSED, ACCEPTED and EXPIRED count
-- Mandate 3: a REJECTED match only, so its pool is empty but its completed visit still counts
INSERT INTO T_MATCH(
    id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price,
    neighborhood_score, price_score, rooms_score, amenities_score, availability_score, furniture_score,
    created_at, modified_at
) VALUES
    (1, 1, 1, 101, 20, 'ACTIVE', 0.9, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (2, 1, 1, 102, 20, 'PROPOSED', 0.9, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (3, 1, 1, 103, 20, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (4, 1, 1, 104, 20, 'EXPIRED', 0.9, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (5, 1, 1, 105, 20, 'REJECTED', 0.9, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (6, 1, 1, 106, 20, 'DISMISSED', 0.9, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (7, 1, 1, 107, 20, 'SHORTLISTED', 0.9, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (8, 1, 3, 101, 20, 'REJECTED', 0.9, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (9, 1, 2, 109, 30, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (10, 2, 9, 901, 90, 'ACTIVE', 0.9, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00');

-- Mandate 1: a fresh request, a future confirmation, a checked-in, a completed, and stale/cancelled ones that don't count
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, created_at
) VALUES
    (7101, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', NOW(), NULL, NOW()),
    (7102, 1, 2, 1, 102, 20, 10, 'CONFIRMED', 'ON_SITE', NOW(), '2099-01-01 09:00:00', NOW()),
    (7103, 1, 3, 1, 103, 20, 10, 'CHECKED_IN', 'ON_SITE', NOW(), '2099-01-01 09:00:00', NOW()),
    (7104, 1, 4, 1, 104, 20, 10, 'COMPLETED', 'ON_SITE', '2000-01-01 09:00:00', '2000-01-01 09:00:00', '2000-01-01 09:00:00'),
    (7105, 1, 5, 1, 105, 20, 10, 'REQUESTED', 'ON_SITE', '2000-01-01 09:00:00', NULL, '2000-01-01 09:00:00'),
    (7106, 1, 6, 1, 106, 20, 10, 'CONFIRMED', 'ON_SITE', '2000-01-01 09:00:00', '2000-01-01 09:00:00', '2000-01-01 09:00:00'),
    (7107, 1, 7, 1, 107, 20, 10, 'CANCELLED', 'ON_SITE', '2000-01-01 09:00:00', NULL, '2000-01-01 09:00:00'),
    (7108, 1, 8, 3, 101, 20, 10, 'COMPLETED', 'ON_SITE', '2000-01-01 09:00:00', '2000-01-01 09:00:00', '2000-01-01 09:00:00'),
    (7109, 1, 9, 2, 109, 30, 10, 'CONFIRMED', 'ON_SITE', NOW(), '2099-01-01 09:00:00', NOW()),
    (7110, 2, 10, 9, 901, 90, 90, 'CONFIRMED', 'ON_SITE', NOW(), '2099-01-01 09:00:00', NOW());
