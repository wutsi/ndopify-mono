INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

-- c-1: draft, no content yet; c-2: sent for signature; c-4: expired; c-5: cancelled; c-6: signed; c-9: other tenant
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, duration_months, signature_deadline, valid_from, valid_until, created_at, signed_at) VALUES
    ('c-1', 1, 'MNDT-2024-000001', 'SEARCH', 'DRAFT', null, 6, null, null, null, '2024-05-20 00:00:00', null),
    ('c-2', 1, 'MNDT-2024-000002', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-2.pdf', 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-4', 1, 'MNDT-2024-000004', 'SEARCH', 'EXPIRED', 'https://cdn.ndopify.com/contracts/c-4.pdf', 6, '2020-06-01 00:00:00', null, null, '2020-05-20 00:00:00', null),
    ('c-5', 1, 'MNDT-2024-000005', 'SEARCH', 'CANCELLED', 'https://cdn.ndopify.com/contracts/c-5.pdf', 6, null, null, null, '2024-05-20 00:00:00', null),
    ('c-6', 1, 'MNDT-2024-000006', 'SEARCH', 'SIGNED', 'https://cdn.ndopify.com/contracts/c-6.pdf', 6, '2024-05-27 00:00:00', '2024-05-21 00:00:00', '2024-11-21 00:00:00', '2024-05-20 00:00:00', '2024-05-21 00:00:00'),
    ('c-9', 2, 'MNDT-2024-000001', 'SEARCH', 'DRAFT', null, 6, null, null, null, '2024-05-20 00:00:00', null);
