INSERT INTO T_TENANT(id, active, name, domain_name, locales, country_code, number_format, currency_code, currency_symbol, monetary_format, date_format, time_format, date_time_format, logo_url, partner_central_url)
VALUES
    (1, true, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', '#,###,##0.00', 'XAF', 'FCFA', '#,###,##0 FCFA', 'yyyy-MM-dd', 'HH:mm', 'yyyy-MM-dd HH:mm', 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/logo.png', 'http://localhost:8081/partner-central');

-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');


INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, kyc_status, created_at, modified_at) VALUES
    (100, 1, 'pending@gmail.com', 'Pending', 'Party', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (300, 1, 'already.verified@gmail.com', 'Already', 'Verified', 4, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@gmail.com', 'Other', 'Tenant', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, identification_type, score, status, retries, error_code, error_message, created_at, modified_at) VALUES
    ('kyc-case-submitted', 1, 100, 1, NULL, 2, NULL, NULL, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-retried', 1, 100, 1, NULL, 2, 1, 'GATEWAY_ERROR', 'Previous failure', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-last-retry', 1, 100, 1, NULL, 2, 2, 'GATEWAY_ERROR', 'Previous failure', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-pending', 1, 300, 1, NULL, 1, NULL, NULL, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-already-verified', 1, 300, 1, 95, 4, NULL, NULL, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-other-tenant', 2, 900, 1, NULL, 1, NULL, NULL, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, tenant_id, party_id, number, type, status, created_at, modified_at) VALUES
    ('payment-method-pending', 1, 100, '+237650000001', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('payment-method-verified', 1, 100, '+237650000002', 1, 2, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
