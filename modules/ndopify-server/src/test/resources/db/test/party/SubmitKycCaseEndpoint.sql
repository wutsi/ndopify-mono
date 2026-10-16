-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, kyc_status, created_at, modified_at) VALUES
    (100, 1, 'complete@gmail.com', 'Complete', 'Party', 'PENDING', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (200, 1, 'no.selfie@gmail.com', 'NoSelfie', 'Party', 'PENDING', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (300, 1, 'missing.back@gmail.com', 'MissingBack', 'Party', 'PENDING', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (400, 1, 'back.no.content@gmail.com', 'BackNoContent', 'Party', 'PENDING', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (500, 1, 'passport@gmail.com', 'Passport', 'Party', 'PENDING', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (600, 1, 'passport.no.front@gmail.com', 'PassportNoFront', 'Party', 'PENDING', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@gmail.com', 'Other', 'Tenant', 'PENDING', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, identification_type, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-complete', 1, 100, 'NATIONAL_ID', NULL, 'PENDING', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-no-selfie', 1, 200, 'NATIONAL_ID', NULL, 'PENDING', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-missing-back', 1, 300, 'NATIONAL_ID', NULL, 'PENDING', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-back-no-content', 1, 400, 'NATIONAL_ID', NULL, 'PENDING', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-passport', 1, 500, 'PASSPORT', NULL, 'PENDING', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-passport-no-front', 1, 600, 'PASSPORT', NULL, 'PENDING', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-submitted', 1, 100, 'NATIONAL_ID', NULL, 'SUBMITTED', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-rejected', 1, 100, 'NATIONAL_ID', 40, 'REJECTED', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-other-tenant', 2, 900, 'NATIONAL_ID', NULL, 'PENDING', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_IMAGE(id, tenant_id, case_id, identification_type, image_type, storage_type, path, mime_type, created_at, modified_at) VALUES
    ('complete-front', 1, 'kyc-case-complete', 'NATIONAL_ID', 'FRONT', 'LOCAL', 'kyc/kyc-case-complete/images/complete-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('complete-back', 1, 'kyc-case-complete', 'NATIONAL_ID', 'BACK', 'LOCAL', 'kyc/kyc-case-complete/images/complete-back.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('complete-selfie', 1, 'kyc-case-complete', 'NATIONAL_ID', 'SELFIE', 'LOCAL', 'kyc/kyc-case-complete/images/complete-selfie.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('no-selfie-front', 1, 'kyc-case-no-selfie', 'NATIONAL_ID', 'FRONT', 'LOCAL', 'kyc/kyc-case-no-selfie/images/no-selfie-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('no-selfie-back', 1, 'kyc-case-no-selfie', 'NATIONAL_ID', 'BACK', 'LOCAL', 'kyc/kyc-case-no-selfie/images/no-selfie-back.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('missing-back-front', 1, 'kyc-case-missing-back', 'NATIONAL_ID', 'FRONT', 'LOCAL', 'kyc/kyc-case-missing-back/images/missing-back-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('back-no-content-front', 1, 'kyc-case-back-no-content', 'NATIONAL_ID', 'FRONT', 'LOCAL', 'kyc/kyc-case-back-no-content/images/back-no-content-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('back-no-content-back', 1, 'kyc-case-back-no-content', 'NATIONAL_ID', 'BACK', 'LOCAL', NULL, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('passport-front', 1, 'kyc-case-passport', 'PASSPORT', 'FRONT', 'LOCAL', 'kyc/kyc-case-passport/images/passport-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('passport-no-front-selfie', 1, 'kyc-case-passport-no-front', 'PASSPORT', 'SELFIE', 'LOCAL', 'kyc/kyc-case-passport-no-front/images/passport-no-front-selfie.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('other-tenant-front', 2, 'kyc-case-other-tenant', 'NATIONAL_ID', 'FRONT', 'LOCAL', 'kyc/kyc-case-other-tenant/images/other-tenant-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('other-tenant-back', 2, 'kyc-case-other-tenant', 'NATIONAL_ID', 'BACK', 'LOCAL', 'kyc/kyc-case-other-tenant/images/other-tenant-back.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
