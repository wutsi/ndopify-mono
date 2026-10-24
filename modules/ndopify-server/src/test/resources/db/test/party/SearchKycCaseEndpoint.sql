-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, '007@gmail.com', 'J', 'Bond', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, party_id, type, issuing_country_code, status, created_at, modified_at) VALUES
    ('identification-100', 100, 'NATIONAL_ID', 'CM', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('identification-101', 101, 'NATIONAL_ID', 'US', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_CASE(id, party_id, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-100-a', 100, NULL, 'PENDING_VERIFICATION', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-100-b', 100, 80, 'VERIFIED', NULL, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    ('kyc-case-101-a', 101, NULL, 'PENDING_VERIFICATION', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
