INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, e_signature_portal_url) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'https://sign.ndopify.com');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'john.smith@gmail.com', 'John', 'Smith'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible');

-- c-1: signed, its content stored by the test; c-2: not signed yet
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, content_hash, duration_months, signature_deadline, valid_from, valid_until, created_at, signed_at) VALUES
    ('c-1', 1, 'MNDT-2024-000001', 'SEARCH', 'SIGNED', null, null, 6, '2099-06-01 00:00:00', '2024-05-21 00:00:00', '2099-11-21 00:00:00', '2024-05-20 00:00:00', '2024-05-21 00:00:00'),
    ('c-2', 1, 'MNDT-2024-000002', 'SEARCH', 'PENDING_SIGNATURE', null, null, 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null);

-- c-1 was signed by its customer and its guarantor; its agent doesn't sign
INSERT INTO T_CONTRACT_PARTY(id, tenant_id, contract_id, party_id, role, legal_name, legal_email, signature_required, signed, ip_address, user_agent, signed_at, signature_salt, signature_hash) VALUES
    ('cp-11', 1, 'c-1', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, true, '10.0.0.1', 'Mozilla/5.0', '2024-05-21 00:00:00', '11111111111111111111111111111111', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
    ('cp-12', 1, 'c-1', 200, 'AGENT', 'Kim Possible', 'kim.possible@gmail.com', false, false, null, null, null, null, null),
    ('cp-13', 1, 'c-1', 101, 'GUARANTOR', 'John Smith', 'john.smith@gmail.com', true, true, '10.0.0.2', 'Mozilla/5.0', '2024-05-21 00:00:00', '22222222222222222222222222222222', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'),
    ('cp-21', 1, 'c-2', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, false, null, null, null, null, null);
