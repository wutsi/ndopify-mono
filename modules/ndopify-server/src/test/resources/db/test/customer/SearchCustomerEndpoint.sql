-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 'kim.possible@gmail.com', 'Kim', 'Possible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 1, 'ash.ketchum@gmail.com', 'Ash', 'Ketchum', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_CUSTOMER (id, tenant_id, party_id, whatsapp_number, created_at, modified_at) VALUES
    (1, 1, 100, '+23761111111', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 1, 101, '+23761111112', '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (3, 1, 102, null, '2024-06-12 00:00:00', '2024-06-12 00:00:00'),
    (4, 2, 900, '+23762222222', '2024-06-13 00:00:00', '2024-06-13 00:00:00');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (103, 1, 'agent@gmail.com', 'The', 'Agent', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 103, 2370201);

-- The caller is user 11 (party 100, customer 1), 12 (party 101, customer 2), 13 (party 103, agent 20);
-- user 14 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'agent@gmail.com', false, '2024-06-10 00:00:00'),
    (14, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false),
    (12, 1, 12, 101, false),
    (13, 1, 13, 103, false);

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (11, 1, 1, 20, 'ASSIGNED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (12, 1, 2, 20, 'ASSIGNED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (13, 1, 3, null, 'PENDING_ASSIGNMENT', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
