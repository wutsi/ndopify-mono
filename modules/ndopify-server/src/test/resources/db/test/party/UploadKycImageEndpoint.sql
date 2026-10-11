INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, tenant_id, party_id, type, issuing_country_code, status, created_at, modified_at) VALUES
    ('identification-100', 1, 100, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('identification-900', 2, 900, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-100', 1, 100, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-900', 2, 900, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_IMAGE(id, tenant_id, case_id, identification_type, image_type, storage_type, path, mime_type, created_at, modified_at) VALUES
    ('kyc-image-100-back', 1, 'kyc-case-100', 1, 2, 1, 'kyc/kyc-case-100/images/kyc-image-100-back.png', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
