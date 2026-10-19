INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, partner_central_url) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'https://partner.ndopify.com'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', null);

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'john.smith@gmail.com', 'John', 'Smith'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

-- The users who sign: 11 is party 100, 12 is party 101
INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-05-20 00:00:00'),
    (12, 'john.smith@gmail.com', false, '2024-05-20 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false),
    (12, 1, 12, 101, false);

-- c-1: nobody signed yet; c-2: only its last party left to sign; c-3: already signed; c-4: expired; c-5: draft;
-- c-9: other tenant
-- c-6: one party left, mandate cancelled meanwhile; c-7: one party left, no mandate
-- c-8: one party left to sign, plus a party that doesn't sign it (an agent)
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, content_hash, duration_months, signature_deadline, valid_from, valid_until, created_at, signed_at) VALUES
    ('c-1', 1, 'MNDT-2024-000001', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-1.pdf', '1111111111111111111111111111111111111111111111111111111111111111', 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-2', 1, 'MNDT-2024-000002', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-2.pdf', null, 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-3', 1, 'MNDT-2024-000003', 'SEARCH', 'SIGNED', 'https://cdn.ndopify.com/contracts/c-3.pdf', null, 6, '2024-05-27 00:00:00', '2024-05-21 00:00:00', '2024-11-21 00:00:00', '2024-05-20 00:00:00', '2024-05-21 00:00:00'),
    ('c-4', 1, 'MNDT-2019-000001', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-4.pdf', null, 6, '2020-06-01 00:00:00', null, null, '2019-05-20 00:00:00', null),
    ('c-5', 1, 'MNDT-2024-000005', 'SEARCH', 'DRAFT', 'https://cdn.ndopify.com/contracts/c-5.pdf', null, 6, null, null, null, '2024-05-20 00:00:00', null),
    ('c-6', 1, 'MNDT-2024-000006', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-6.pdf', null, 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-7', 1, 'MNDT-2024-000007', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-7.pdf', null, 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-8', 1, 'MNDT-2024-000008', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-8.pdf', null, 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-9', 2, 'MNDT-2024-000001', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-9.pdf', null, 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null);

INSERT INTO T_CONTRACT_PARTY(id, tenant_id, contract_id, party_id, role, legal_name, legal_email, signature_required, signed, ip_address, user_agent, signed_at, signature_hash) VALUES
    ('cp-11', 1, 'c-1', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, false, null, null, null, null),
    ('cp-12', 1, 'c-1', 101, 'GUARANTOR', 'John Smith', 'john.smith@gmail.com', true, false, null, null, null, null),
    ('cp-21', 1, 'c-2', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, true, '10.0.0.1', 'Mozilla/5.0', '2024-05-21 00:00:00', '3333333333333333333333333333333333333333333333333333333333333333'),
    ('cp-22', 1, 'c-2', 101, 'GUARANTOR', 'John Smith', 'john.smith@gmail.com', true, false, null, null, null, null),
    ('cp-31', 1, 'c-3', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, true, '10.0.0.1', 'Mozilla/5.0', '2024-05-21 00:00:00', null),
    ('cp-41', 1, 'c-4', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, false, null, null, null, null),
    ('cp-51', 1, 'c-5', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, false, null, null, null, null),
    ('cp-61', 1, 'c-6', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, false, null, null, null, null),
    ('cp-71', 1, 'c-7', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, false, null, null, null, null),
    ('cp-81', 1, 'c-8', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, false, null, null, null, null),
    ('cp-82', 1, 'c-8', 101, 'AGENT', 'John Smith', 'john.smith@gmail.com', false, false, null, null, null, null),
    ('cp-91', 2, 'c-9', 900, 'MANDATOR', 'Other Tenant', 'other.tenant@example.com', true, false, null, null, null, null);

-- The mandates of the contracts: 1 (c-1) and 2 (c-2) wait for approval, 6 (c-6) was cancelled meanwhile
INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

-- The agent of the mandates, told when its mandate is approved
INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201);

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, contract_id, status, mandate_type, listing_type, city_id, price,
    price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (1, 1, 10, 20, 'c-1', 'PENDING_APPROVAL', 'SEARCH', 'RENTAL', 2370201, 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 1, 10, 20, 'c-2', 'PENDING_APPROVAL', 'SEARCH', 'RENTAL', 2370201, 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (6, 1, 10, 20, 'c-6', 'CANCELLED', 'SEARCH', 'RENTAL', 2370201, 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
