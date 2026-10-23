INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'john.smith@gmail.com', 'John', 'Smith');

-- 11 is party 100, 12 is party 101, 13 is a member without party, 14 is the admin
INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-05-20 00:00:00'),
    (12, 'john.smith@gmail.com', false, '2024-05-20 00:00:00'),
    (13, 'jane.doe@gmail.com', false, '2024-05-20 00:00:00'),
    (14, 'admin@ndopify.com', false, '2024-05-20 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false),
    (12, 1, 12, 101, false),
    (13, 1, 13, null, false),
    (14, 1, 14, null, false);

-- c-1: party 100's; c-2: party 101's; c-3: party 100's, draft
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, content_hash, duration_months, signature_deadline, created_at) VALUES
    ('c-1', 1, 'MNDT-2024-000001', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-1.pdf', '1111111111111111111111111111111111111111111111111111111111111111', 6, '2099-06-01 00:00:00', '2024-05-20 00:00:00'),
    ('c-2', 1, 'MNDT-2024-000002', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-2.pdf', '1111111111111111111111111111111111111111111111111111111111111111', 6, '2099-06-01 00:00:00', '2024-05-21 00:00:00'),
    ('c-3', 1, 'MNDT-2024-000003', 'SEARCH', 'DRAFT', null, null, 6, null, '2024-05-22 00:00:00');

INSERT INTO T_CONTRACT_PARTY(id, tenant_id, contract_id, party_id, role, legal_name, legal_email, signed) VALUES
    ('cp-1', 1, 'c-1', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', false),
    ('cp-2', 1, 'c-2', 101, 'MANDATOR', 'John Smith', 'john.smith@gmail.com', false),
    ('cp-3', 1, 'c-3', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', false);
