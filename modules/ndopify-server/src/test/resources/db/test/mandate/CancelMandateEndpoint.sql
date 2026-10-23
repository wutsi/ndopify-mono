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

-- The mandates' contracts. 5: draft   6: waiting for the customer's signature   2: signed   7: expired
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, duration_months) VALUES
    ('c-2', 1, 'MNDT-2026-000002', 'SEARCH', 'SIGNED', 'https://cdn.ndopify.com/2.html', 6),
    ('c-5', 1, 'MNDT-2026-000005', 'SEARCH', 'DRAFT', null, 6),
    ('c-6', 1, 'MNDT-2026-000006', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/6.html', 6),
    ('c-7', 1, 'MNDT-2026-000007', 'SEARCH', 'EXPIRED', 'https://cdn.ndopify.com/7.html', 6);

-- 1: waiting for an agent, with a PENDING offer   2: approved   3: closed   4: cancelled   9: other tenant
-- 5: assigned, with a draft contract   6: waiting for approval   7: assigned, with an expired contract
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, contract_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at, approved_at, closed_at
) VALUES
    (1, 1, 10, null, null, 'PENDING_ASSIGNMENT', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', null, null),
    (2, 1, 10, 20, 'c-2', 'APPROVED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00', null),
    (5, 1, 10, 20, 'c-5', 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', null, null),
    (6, 1, 10, 20, 'c-6', 'PENDING_APPROVAL', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', null, null),
    (7, 1, 10, 20, 'c-7', 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', null, null),
    (3, 1, 10, 20, null, 'CLOSED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2024-06-20 00:00:00'),
    (4, 1, 10, 20, null, 'CANCELLED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', null, '2024-06-20 00:00:00'),
    (9, 2, 90, 90, null, 'APPROVED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00', null);

-- 1: offered to agent 20, not answered yet   2: accepted
INSERT INTO T_ASSIGNMENT(id, tenant_id, mandate_id, agent_id, status, score, created_at, modified_at, expires_at) VALUES
    (1, 1, 1, 20, 'PENDING', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00'),
    (2, 1, 2, 20, 'ACCEPTED', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00');
