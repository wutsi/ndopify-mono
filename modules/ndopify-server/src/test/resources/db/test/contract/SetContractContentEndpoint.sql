INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible');

-- c-1: draft, no content URL yet (but a content hash, to be cleared); c-2: sent for signature; c-4: expired; c-5: cancelled; c-6: signed; c-9: other tenant
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, content_hash, duration_months, signature_deadline, valid_from, valid_until, created_at, signed_at) VALUES
    ('c-1', 1, 'MNDT-2024-000001', 'SEARCH', 'DRAFT', null, '1111111111111111111111111111111111111111111111111111111111111111', 6, null, null, null, '2024-05-20 00:00:00', null),
    ('c-2', 1, 'MNDT-2024-000002', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-2.pdf', '1111111111111111111111111111111111111111111111111111111111111111', 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-4', 1, 'MNDT-2024-000004', 'SEARCH', 'EXPIRED', 'https://cdn.ndopify.com/contracts/c-4.pdf', null, 6, '2020-06-01 00:00:00', null, null, '2020-05-20 00:00:00', null),
    ('c-5', 1, 'MNDT-2024-000005', 'SEARCH', 'CANCELLED', 'https://cdn.ndopify.com/contracts/c-5.pdf', null, 6, null, null, null, '2024-05-20 00:00:00', null),
    ('c-6', 1, 'MNDT-2024-000006', 'SEARCH', 'SIGNED', 'https://cdn.ndopify.com/contracts/c-6.pdf', null, 6, '2024-05-27 00:00:00', '2024-05-21 00:00:00', '2024-11-21 00:00:00', '2024-05-20 00:00:00', '2024-05-21 00:00:00'),
    ('c-9', 2, 'MNDT-2024-000001', 'SEARCH', 'DRAFT', null, null, 6, null, null, null, '2024-05-20 00:00:00', null);

-- c-2's first party already signed it, before its content changes
INSERT INTO T_CONTRACT_PARTY(id, tenant_id, contract_id, party_id, role, legal_name, legal_email, signed, ip_address, user_agent, signed_at, signature_hash) VALUES
    ('cp-21', 1, 'c-2', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, '10.0.0.1', 'Mozilla/5.0', '2024-05-21 00:00:00', '3333333333333333333333333333333333333333333333333333333333333333');
