INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active, e_signature_portal_url) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true, 'https://sign.ndopify.com'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', true, 'https://sign.other.com'),
    (3, 'Inactive', 'inactive.localhost', 'fr-CM, en-CM', 'CM', false, 'https://sign.inactive.com');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible');

-- Content stored by the test for each of them, so only the selection decides
-- To generate: c-1: signed an hour ago; c-21: of tenant 2, signed an hour ago
-- To keep: c-2: signed 2 minutes ago (left to the listener); c-3: signed 8 days ago (given up);
-- c-4: already has its PDF; c-5: not signed yet; c-31: of an inactive tenant
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, signed_content_url, duration_months, signature_deadline, valid_from, valid_until, created_at, signed_at) VALUES
    ('c-1', 1, 'MNDT-2024-000001', 'SEARCH', 'SIGNED', null, null, 6, '2099-06-01 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR), '2099-11-21 00:00:00', '2024-05-20 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR)),
    ('c-2', 1, 'MNDT-2024-000002', 'SEARCH', 'SIGNED', null, null, 6, '2099-06-01 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 2 MINUTE), '2099-11-21 00:00:00', '2024-05-20 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 2 MINUTE)),
    ('c-3', 1, 'MNDT-2024-000003', 'SEARCH', 'SIGNED', null, null, 6, '2099-06-01 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 8 DAY), '2099-11-21 00:00:00', '2024-05-20 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 8 DAY)),
    ('c-4', 1, 'MNDT-2024-000004', 'SEARCH', 'SIGNED', null, 'https://s.com/c-4.pdf', 6, '2099-06-01 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR), '2099-11-21 00:00:00', '2024-05-20 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR)),
    ('c-5', 1, 'MNDT-2024-000005', 'SEARCH', 'PENDING_SIGNATURE', null, null, 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-21', 2, 'MNDT-2024-000001', 'SEARCH', 'SIGNED', null, null, 6, '2099-06-01 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR), '2099-11-21 00:00:00', '2024-05-20 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR)),
    ('c-31', 3, 'MNDT-2024-000001', 'SEARCH', 'SIGNED', null, null, 6, '2099-06-01 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR), '2099-11-21 00:00:00', '2024-05-20 00:00:00', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR));

INSERT INTO T_CONTRACT_PARTY(id, tenant_id, contract_id, party_id, role, legal_name, legal_email, signature_required, signed, ip_address, user_agent, signed_at, signature_salt, signature_hash) VALUES
    ('cp-11', 1, 'c-1', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, true, '10.0.0.1', 'Mozilla/5.0', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR), '11111111111111111111111111111111', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');
