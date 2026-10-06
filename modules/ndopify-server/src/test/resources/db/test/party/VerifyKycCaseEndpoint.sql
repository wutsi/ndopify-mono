INSERT INTO T_PARTY(id, email, first_name, last_name, kyc_status, created_at, modified_at) VALUES
    (100, 'identification.verified@gmail.com', 'Identification', 'Verified', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (200, 'identification.rejected@gmail.com', 'Identification', 'Rejected', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (300, 'already.verified@gmail.com', 'Already', 'Verified', 3, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, party_id, type, issuing_country_code, status, created_at, modified_at) VALUES
    ('identification-100', 100, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('identification-200', 200, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('identification-300', 300, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, party_id, number, type, status, created_at, modified_at) VALUES
    ('payment-method-100', 100, '+237650000100', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('payment-method-200', 200, '+237650000200', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, party_id, identification_id, payment_method_id, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-identification-verified', 100, 'identification-100', 'payment-method-100', NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-identification-rejected', 200, 'identification-200', 'payment-method-200', NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-already-verified', 300, 'identification-300', NULL, 95, 3, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_VERIFICATION(id, case_id, type, status, score, error_code, created_at, modified_at) VALUES
    ('verification-id-verified', 'kyc-case-identification-verified', 1, 3, 95, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('verification-pm-rejected', 'kyc-case-identification-verified', 2, 4, 10, 'INACTIVE', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),

    ('verification-id-rejected', 'kyc-case-identification-rejected', 1, 4, 50, 'INVALID', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('verification-pm-verified', 'kyc-case-identification-rejected', 2, 3, 100, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
