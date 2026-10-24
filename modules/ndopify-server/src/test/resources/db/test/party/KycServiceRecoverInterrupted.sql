INSERT INTO T_TENANT(id, active, name, domain_name, locales, country_code, number_format, currency_code, currency_symbol, monetary_format, date_format, time_format, date_time_format, logo_url, partner_central_url)
VALUES
    (1, true, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', '#,###,##0.00', 'XAF', 'FCFA', '#,###,##0 FCFA', 'yyyy-MM-dd', 'HH:mm', 'yyyy-MM-dd HH:mm', NULL, NULL),
    (2, true, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', '#,###,##0.00', 'XAF', 'FCFA', '#,###,##0 FCFA', 'yyyy-MM-dd', 'HH:mm', 'yyyy-MM-dd HH:mm', NULL, NULL);

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, kyc_status, created_at, modified_at) VALUES
    (100, 1, 'pending@gmail.com', 'Pending', 'Party', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@gmail.com', 'Other', 'Tenant', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, identification_type, score, status, retries, error_code, error_message, created_at, modified_at) VALUES
    ('kyc-case-interrupted', 1, 100, 'NATIONAL_ID', NULL, 'UNDER_VERIFICATION', NULL, NULL, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-interrupted-last-retry', 1, 100, 'NATIONAL_ID', NULL, 'UNDER_VERIFICATION', 2, 'GATEWAY_ERROR', 'Previous failure', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-in-progress', 1, 100, 'NATIONAL_ID', NULL, 'UNDER_VERIFICATION', NULL, NULL, NULL, '2024-06-10 00:00:00', UTC_TIMESTAMP()), -- UTC, like the app (serverTimezone=UTC)
    ('kyc-case-submitted', 1, 100, 'NATIONAL_ID', NULL, 'SUBMITTED', NULL, NULL, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-other-tenant', 2, 900, 'NATIONAL_ID', NULL, 'UNDER_VERIFICATION', NULL, NULL, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
