-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

-- 100: customer Ray Sponsible (user 13, the caller)   101: another party owning taken@example.com
-- 102: a party that is neither the customer nor an agent (user 14)
-- 200: agent 20 (user 11)   300: agent 30 (user 12)   900: tenant 2
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'taken@example.com', 'Taken', 'Address'),
    (102, 1, 'another.party@example.com', 'Another', 'Party'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (14, 'another.party@example.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false),
    (13, 1, 13, 100, false),
    (14, 1, 14, 102, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id, whatsapp_number) VALUES
    (10, 1, 100, '+237699123456'),
    (90, 2, 900, '+237699000999');

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201);

-- 3, 4: out for signature   5: signed   6: still a draft
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, duration_months) VALUES
    ('c-3', 1, 'MNDT-2026-000003', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/3.html', 6),
    ('c-4', 1, 'MNDT-2026-000004', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/4.html', 6),
    ('c-5', 1, 'MNDT-2026-000005', 'SEARCH', 'SIGNED', 'https://cdn.ndopify.com/5.html', 6),
    ('c-6', 1, 'MNDT-2026-000006', 'SEARCH', 'DRAFT', null, 6);

-- 1: assigned   2: another agent's   3, 4: out for signature   5: approved
-- 6: closed   7: cancelled   8: another tenant's
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, contract_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, comments, created_at, modified_at
) VALUES
    (1, 1, 10, 20, null, 'ASSIGNED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Before', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 1, 10, 30, null, 'APPROVED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Not mine', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (3, 1, 10, 20, 'c-3', 'PENDING_APPROVAL', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Before', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (4, 1, 10, 20, 'c-4', 'PENDING_APPROVAL', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Before', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (5, 1, 10, 20, 'c-5', 'APPROVED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Signed', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (6, 1, 10, 20, null, 'CLOSED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Closed', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (7, 1, 10, 20, null, 'CANCELLED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Cancelled', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (8, 2, 90, null, null, 'ASSIGNED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Other tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
