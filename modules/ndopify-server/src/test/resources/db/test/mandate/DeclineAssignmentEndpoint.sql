-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

-- 100: customer   200: agent 20 (user 11, the caller)   300: agent 30 (user 12)   900: other tenant
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
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
    (90, 2, 900);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201),
    (90, 2, 900, 2370201);

-- 1 is created now: declining it re-dispatches it (no verified agent here, so nothing is offered), and an older one
-- would go MANUAL_ASSIGNMENT
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (1, 1, 10, null, 'PENDING_ASSIGNMENT', 'TENANT', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', NOW(), NOW()),
    (2, 1, 10, null, 'PENDING_ASSIGNMENT', 'TENANT', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (3, 1, 10, null, 'PENDING_ASSIGNMENT', 'TENANT', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (4, 1, 10, null, 'PENDING_ASSIGNMENT', 'TENANT', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (5, 1, 10, null, 'PENDING_ASSIGNMENT', 'TENANT', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (9, 2, 90, null, 'PENDING_ASSIGNMENT', 'TENANT', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 1: offered to the caller   2: offered to another agent   3: already accepted   4: past its TTL
-- 5: another agent's, already declined   9: other tenant
INSERT INTO T_ASSIGNMENT(id, tenant_id, mandate_id, agent_id, status, score, created_at, modified_at, expires_at) VALUES
    (1, 1, 1, 20, 'PENDING', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00'),
    (2, 1, 2, 30, 'PENDING', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00'),
    (3, 1, 3, 20, 'ACCEPTED', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00'),
    (4, 1, 4, 20, 'PENDING', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2020-01-01 00:00:00'),
    (5, 1, 5, 30, 'DECLINED', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00'),
    (9, 2, 9, 90, 'PENDING', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00');
