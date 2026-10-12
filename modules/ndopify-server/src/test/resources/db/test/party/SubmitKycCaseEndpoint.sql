INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, kyc_status, created_at, modified_at) VALUES
    (100, 1, 'complete@gmail.com', 'Complete', 'Party', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (200, 1, 'no.selfie@gmail.com', 'NoSelfie', 'Party', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (300, 1, 'missing.back@gmail.com', 'MissingBack', 'Party', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (400, 1, 'back.no.content@gmail.com', 'BackNoContent', 'Party', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (500, 1, 'passport@gmail.com', 'Passport', 'Party', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (600, 1, 'passport.no.front@gmail.com', 'PassportNoFront', 'Party', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@gmail.com', 'Other', 'Tenant', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, identification_type, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-complete', 1, 100, 1, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-no-selfie', 1, 200, 1, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-missing-back', 1, 300, 1, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-back-no-content', 1, 400, 1, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-passport', 1, 500, 2, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-passport-no-front', 1, 600, 2, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-submitted', 1, 100, 1, NULL, 2, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-rejected', 1, 100, 1, 40, 5, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-other-tenant', 2, 900, 1, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_IMAGE(id, tenant_id, case_id, identification_type, image_type, storage_type, path, mime_type, created_at, modified_at) VALUES
    ('complete-front', 1, 'kyc-case-complete', 1, 1, 1, 'kyc/kyc-case-complete/images/complete-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('complete-back', 1, 'kyc-case-complete', 1, 2, 1, 'kyc/kyc-case-complete/images/complete-back.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('complete-selfie', 1, 'kyc-case-complete', 1, 3, 1, 'kyc/kyc-case-complete/images/complete-selfie.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('no-selfie-front', 1, 'kyc-case-no-selfie', 1, 1, 1, 'kyc/kyc-case-no-selfie/images/no-selfie-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('no-selfie-back', 1, 'kyc-case-no-selfie', 1, 2, 1, 'kyc/kyc-case-no-selfie/images/no-selfie-back.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('missing-back-front', 1, 'kyc-case-missing-back', 1, 1, 1, 'kyc/kyc-case-missing-back/images/missing-back-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('back-no-content-front', 1, 'kyc-case-back-no-content', 1, 1, 1, 'kyc/kyc-case-back-no-content/images/back-no-content-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('back-no-content-back', 1, 'kyc-case-back-no-content', 1, 2, 1, NULL, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('passport-front', 1, 'kyc-case-passport', 2, 1, 1, 'kyc/kyc-case-passport/images/passport-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('passport-no-front-selfie', 1, 'kyc-case-passport-no-front', 2, 3, 1, 'kyc/kyc-case-passport-no-front/images/passport-no-front-selfie.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('other-tenant-front', 2, 'kyc-case-other-tenant', 1, 1, 1, 'kyc/kyc-case-other-tenant/images/other-tenant-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('other-tenant-back', 2, 'kyc-case-other-tenant', 1, 2, 1, 'kyc/kyc-case-other-tenant/images/other-tenant-back.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
