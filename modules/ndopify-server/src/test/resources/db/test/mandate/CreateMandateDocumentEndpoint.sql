-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Autre', 'other.localhost', 'fr-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

-- 100: customer   200: agent 20 (user 11, the caller)   300: agent 30 (user 12)
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'customer@gmail.com', 'Customer', 'One'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201);

-- 100: the caller's LISTING   101: the caller's SEARCH   200: another agent's   300: CLOSED   301: CANCELLED
-- 400: offered to the caller (PENDING assignment, no agent yet)
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, comments, created_at, modified_at
) VALUES
    (100, 1, 10, 20, 'ASSIGNED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Mine listing', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 10, 20, 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Mine search', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (200, 1, 10, 30, 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Not mine', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (300, 1, 10, 20, 'CLOSED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Closed', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (301, 1, 10, 20, 'CANCELLED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Cancelled', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (400, 1, 10, null, 'PENDING_ASSIGNMENT', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Offered', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_ASSIGNMENT(id, tenant_id, mandate_id, agent_id, status, score, created_at, modified_at, expires_at) VALUES
    (1, 1, 400, 20, 'PENDING', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00');

-- 500: a mandate of tenant 2 (the caller's tenant is 1)
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (500, 2, 'tenant2@gmail.com', 'Two', 'Tenant');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (50, 2, 500);

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, comments, created_at, modified_at
) VALUES
    (500, 2, 50, null, 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Other tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
