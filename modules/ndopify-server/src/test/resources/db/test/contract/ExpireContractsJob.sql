INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', true),
    (3, 'Inactive', 'inactive.localhost', 'fr-CM, en-CM', 'CM', false);

-- To expire: c-1: not signed before its deadline; c-2: signed, past its end date; c-21: of tenant 2, not signed in time
-- To keep: c-3: before its deadline; c-4: draft; c-5: signed, before its end date; c-6: already expired;
-- c-7: cancelled; c-31: of an inactive tenant
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, duration_months, signature_deadline, valid_from, valid_until, created_at, signed_at) VALUES
    ('c-1', 1, 'MNDT-2024-000001', 'SEARCH', 'PENDING_SIGNATURE', '', 6, '2020-06-01 00:00:00', null, null, '2020-05-20 00:00:00', null),
    ('c-2', 1, 'MNDT-2024-000002', 'SEARCH', 'SIGNED', '', 6, '2020-06-01 00:00:00', '2020-05-21 00:00:00', '2020-11-21 00:00:00', '2020-05-20 00:00:00', '2020-05-21 00:00:00'),
    ('c-3', 1, 'MNDT-2024-000003', 'SEARCH', 'PENDING_SIGNATURE', '', 6, '2099-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-4', 1, 'MNDT-2024-000004', 'SEARCH', 'DRAFT', '', 6, null, null, null, '2020-05-20 00:00:00', null),
    ('c-5', 1, 'MNDT-2024-000005', 'SEARCH', 'SIGNED', '', 6, '2020-06-01 00:00:00', '2020-05-21 00:00:00', '2099-11-21 00:00:00', '2020-05-20 00:00:00', '2020-05-21 00:00:00'),
    ('c-6', 1, 'MNDT-2024-000006', 'SEARCH', 'EXPIRED', '', 6, '2020-06-01 00:00:00', null, null, '2020-05-20 00:00:00', null),
    ('c-7', 1, 'MNDT-2024-000007', 'SEARCH', 'CANCELLED', '', 6, '2020-06-01 00:00:00', null, null, '2020-05-20 00:00:00', null),
    ('c-21', 2, 'MNDT-2024-000001', 'SEARCH', 'PENDING_SIGNATURE', '', 6, '2020-06-01 00:00:00', null, null, '2020-05-20 00:00:00', null),
    ('c-31', 3, 'MNDT-2024-000001', 'SEARCH', 'PENDING_SIGNATURE', '', 6, '2020-06-01 00:00:00', null, null, '2020-05-20 00:00:00', null);
