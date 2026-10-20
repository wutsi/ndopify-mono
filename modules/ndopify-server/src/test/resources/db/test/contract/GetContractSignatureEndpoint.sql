INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray@example.com', 'Ray', 'Sponsible'),
    (101, 1, 'agent@example.com', 'Ann', 'Agent'),
    (110, 1, 'pending@example.com', 'Pen', 'Ding'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, signed_content_url, duration_months, created_at) VALUES
    ('c-100', 1, 'MNDT-2026-000042', 'SEARCH', 'SIGNED', 'https://s3.example/c/MNDT-2026-000042-t0k3n.pdf', 6, '2026-10-01 00:00:00'),
    ('c-110', 1, 'MNDT-2026-000043', 'SEARCH', 'PENDING_SIGNATURE', null, 6, '2026-10-01 00:00:00'),
    ('c-200', 2, 'MNDT-2026-000001', 'SEARCH', 'SIGNED', null, 6, '2026-10-01 00:00:00');

INSERT INTO T_CONTRACT_PARTY(id, tenant_id, contract_id, party_id, role, legal_name, legal_email, signed, ip_address, user_agent, signed_at, signature_salt, signature_hash) VALUES
    ('cp-100', 1, 'c-100', 100, 'MANDATOR', 'Ray Sponsible', 'ray@example.com', true, '10.0.0.1', 'Mozilla/5.0', '2026-10-18 14:32:00', '55555555555555555555555555555555', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
    ('cp-101', 1, 'c-100', 101, 'AGENT', 'Ann Agent', 'agent@example.com', false, null, null, null, null, null),
    ('cp-110', 1, 'c-110', 110, 'MANDATOR', 'Pen Ding', 'pending@example.com', true, '10.0.0.2', 'Mozilla/5.0', '2026-10-18 15:00:00', '66666666666666666666666666666666', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'),
    ('cp-200', 2, 'c-200', 900, 'MANDATOR', 'Other Tenant', 'other.tenant@example.com', true, '10.0.0.3', 'Mozilla/5.0', '2026-10-18 16:00:00', '77777777777777777777777777777777', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb');
