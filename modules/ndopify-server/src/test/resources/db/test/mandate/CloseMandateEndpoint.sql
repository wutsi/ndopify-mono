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
