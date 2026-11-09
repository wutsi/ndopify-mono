-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

-- 100: customer   200: agent 20   900: other tenant
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (90, 2, 900);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (90, 2, 900, 2370201);

-- 1: approved   2: assigned, not approved yet   3: closed   4: cancelled   9: other tenant
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at, approved_at, closed_at
) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00', null),
    (2, 1, 10, 20, 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', null, null),
    (3, 1, 10, 20, 'CLOSED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2024-06-20 00:00:00'),
    (4, 1, 10, 20, 'CANCELLED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', null, '2024-06-20 00:00:00'),
    (9, 2, 90, 90, 'APPROVED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00', null);

-- LISTING mandates, approved: 5 for sale, its listing published   6 for rent, its listing published   7 its listing not published yet
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    price, currency_code, created_at, modified_at, approved_at
) VALUES
    (5, 1, 10, 20, 'APPROVED', 'LISTING', 'SALE', 2370201, 'HOUSE', 50000000, 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (6, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', 150000, 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (7, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', 150000, 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, listing_type, status, city_id, modified_at) VALUES
    (50, 1, 5, 20, 'SALE', 'PUBLISHED', 2370201, '2024-06-10 00:00:00'),
    (60, 1, 6, 20, 'RENTAL', 'PUBLISHED', 2370201, '2024-06-10 00:00:00'),
    (70, 1, 7, 20, 'RENTAL', 'DRAFT', 2370201, '2024-06-10 00:00:00');

-- Another agent, for a listing the caller doesn't own
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (201, 1, 'other.agent@gmail.com', 'Other', 'Agent');
INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES (21, 1, 201, 2370201);

-- 8: another agent's LISTING mandate, 80 its published listing
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type,
                      city_id, property_type, price, currency_code, created_at, modified_at, approved_at)
VALUES
    (8, 1, 10, 21, 'APPROVED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', 150000, 'XAF',
     '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, listing_type, status, city_id, modified_at)
VALUES (80, 1, 8, 21, 'RENTAL', 'PUBLISHED', 2370201, '2024-06-10 00:00:00');

-- 100: search 1 x listing 60, the deal
-- 101: search 1 x listing 50, proposed (not accepted)
-- 102: search 1 x listing 80, another agent's listing
-- 103: search 1 x listing 70, listing only DRAFT
INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, listing_price, created_at, modified_at)
VALUES
    (100, 1, 1, 60, 20, 'ACCEPTED', 150000, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 1, 50, 20, 'PROPOSED', 50000000, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 1, 1, 80, 20, 'ACCEPTED', 150000, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (103, 1, 1, 70, 20, 'ACCEPTED', 150000, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
