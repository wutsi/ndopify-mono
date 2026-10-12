INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, tenant_id, party_id, type, issuing_country_code, status, created_at, modified_at) VALUES
    ('identification-100', 1, 100, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('identification-900', 2, 900, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-100', 1, 100, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    -- One case per status other than PENDING (status = KycStatus ordinal): none of them accepts images.
    ('kyc-case-100-unknown', 1, 100, NULL, 0, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-submitted', 1, 100, NULL, 2, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-in-progress', 1, 100, NULL, 3, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-verified', 1, 100, NULL, 4, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-rejected', 1, 100, NULL, 5, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-requires-manual-review', 1, 100, NULL, 6, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-cancelled', 1, 100, NULL, 7, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-900', 2, 900, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_IMAGE(id, tenant_id, case_id, identification_type, image_type, storage_type, path, mime_type, created_at, modified_at) VALUES
    ('kyc-image-100-back', 1, 'kyc-case-100', 1, 2, 1, 'kyc/kyc-case-100/images/kyc-image-100-back.png', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
