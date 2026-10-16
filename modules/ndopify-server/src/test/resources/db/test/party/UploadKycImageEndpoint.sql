-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, tenant_id, party_id, type, issuing_country_code, status, created_at, modified_at) VALUES
    ('identification-100', 1, 100, 'NATIONAL_ID', 'CM', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('identification-900', 2, 900, 'NATIONAL_ID', 'CM', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-100', 1, 100, NULL, 'PENDING', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    -- One case per status other than PENDING: none of them accepts images.
    ('kyc-case-100-unknown', 1, 100, NULL, 'UNKNOWN', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-submitted', 1, 100, NULL, 'SUBMITTED', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-in-progress', 1, 100, NULL, 'IN_PROGRESS', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-verified', 1, 100, NULL, 'VERIFIED', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-rejected', 1, 100, NULL, 'REJECTED', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-requires-manual-review', 1, 100, NULL, 'REQUIRES_MANUAL_REVIEW', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-cancelled', 1, 100, NULL, 'CANCELLED', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-900', 2, 900, NULL, 'PENDING', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_IMAGE(id, tenant_id, case_id, identification_type, image_type, storage_type, path, mime_type, created_at, modified_at) VALUES
    ('kyc-image-100-back', 1, 'kyc-case-100', 'NATIONAL_ID', 'BACK', 'LOCAL', 'kyc/kyc-case-100/images/kyc-image-100-back.png', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
