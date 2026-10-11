INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, kyc_status, created_at, modified_at) VALUES
    (100, 1, 'pending@gmail.com', 'Pending', 'Party', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (300, 1, 'already.verified@gmail.com', 'Already', 'Verified', 3, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@gmail.com', 'Other', 'Tenant', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, identification_type, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-pending', 1, 100, 1, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-already-verified', 1, 300, 1, 95, 3, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-other-tenant', 2, 900, 1, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, tenant_id, party_id, number, type, status, created_at, modified_at) VALUES
    ('payment-method-pending', 1, 100, '+237650000001', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('payment-method-verified', 1, 100, '+237650000002', 1, 2, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
