INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'john.smith@gmail.com', 'John', 'Smith'),
    (102, 1, 'jane.doe@gmail.com', 'Jane', 'Doe'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, duration_months, signature_deadline, valid_from, valid_until, created_at, signed_at) VALUES
    ('c-1', 1, 'MNDT-2024-000001', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-1.pdf', 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-2', 1, 'MNDT-2024-000002', 'SEARCH', 'SIGNED', null, 12, '2024-06-01 00:00:00', '2024-05-25 00:00:00', '2025-05-25 00:00:00', '2024-05-21 00:00:00', '2024-05-25 00:00:00'),
    ('c-3', 1, 'BAIL-2024-000001', 'LEASE_AGREEMENT', 'DRAFT', null, 6, null, null, null, '2024-05-22 00:00:00', null),
    ('c-9', 2, 'MNDT-2024-000001', 'SEARCH', 'PENDING_SIGNATURE', null, 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null);

-- Party 100 is in c-1 three times (cp-1, cp-2, cp-11): c-1 must still be returned once. Party 101 is its co-party (cp-12)
INSERT INTO T_CONTRACT_PARTY(id, tenant_id, contract_id, party_id, role, legal_name, legal_email, signed) VALUES
    ('cp-1', 1, 'c-1', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', false),
    ('cp-2', 1, 'c-1', 100, 'GUARANTOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', false),
    ('cp-3', 1, 'c-2', 101, 'MANDATOR', 'John Smith', 'john.smith@gmail.com', true),
    ('cp-4', 1, 'c-3', 102, 'MANDATOR', 'Jane Doe', 'jane.doe@gmail.com', false),
    ('cp-9', 2, 'c-9', 900, 'MANDATOR', 'Other Tenant', 'other.tenant@example.com', false),
    ('cp-11', 1, 'c-1', 100, 'GUARANTOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', false),
    ('cp-21', 1, 'c-2', 101, 'GUARANTOR', 'John Smith', 'john.smith@gmail.com', false),
    ('cp-12', 1, 'c-1', 101, 'AGENT', 'John Smith', 'john.smith@gmail.com', false);

-- User 11 is party 100 (Ray Sponsible), so it searches as that party
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-05-20 00:00:00');

INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false);
