-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, '007@gmail.com', 'J', 'Bond', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, party_id, number, provider_name, holder_name, type, status, created_at, modified_at) VALUES
    ('pm-100-a', 100, '+237671234567', 'MTN', 'Ray Sponsible', 'MOBILE_MONEY', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('pm-100-b', 100, '+237650000000', 'Orange', 'Ray Sponsible', 'MOBILE_MONEY', 'VERIFIED', '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    ('pm-100-c', 100, '1234567890', 'UBA', 'Ray Sponsible', 'BANK_ACCOUNT', 'PENDING_VERIFICATION', '2024-06-12 00:00:00', '2024-06-12 00:00:00'),
    ('pm-101-a', 101, '+237699999999', 'MTN', 'J Bond', 'MOBILE_MONEY', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- The caller is user 11 (party 100), 12 (party 101); user 13 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, '007@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false),
    (12, 1, 12, 101, false);
