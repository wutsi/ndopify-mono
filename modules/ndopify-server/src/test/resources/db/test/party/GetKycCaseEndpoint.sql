INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, tenant_id, party_id, type, issuing_country_code, status, created_at, modified_at) VALUES
    ('identification-100', 1, 100, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('identification-900', 2, 900, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, tenant_id, party_id, number, type, status, created_at, modified_at) VALUES
    ('payment-method-100', 1, 100, '+237650000000', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('payment-method-900', 2, 900, '+237659000000', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, identification_id, payment_method_id, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-100', 1, 100, 'identification-100', 'payment-method-100', 80, 3, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-200', 1, 100, 'identification-100', NULL, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-900', 2, 900, 'identification-900', 'payment-method-900', 80, 3, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_VERIFICATION(id, case_id, type, status, score, error_code, created_at, modified_at) VALUES
    ('verification-100', 'kyc-case-100', 1, 3, 80, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('verification-200', 'kyc-case-100', 2, 3, 90, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
