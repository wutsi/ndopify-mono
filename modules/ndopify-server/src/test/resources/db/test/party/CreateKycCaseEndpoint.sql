-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (200, 'other.party@gmail.com', 'Other', 'Party', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- The caller is user 11, member of party 100; user 12 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false);

INSERT INTO T_IDENTIFICATION(id, party_id, type, issuing_country_code, status, created_at, modified_at) VALUES
    ('identification-100', 100, 'NATIONAL_ID', 'CM', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, party_id, number, type, status, created_at, modified_at) VALUES
    ('payment-method-100', 100, '+237650000000', 'MOBILE_MONEY', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('payment-method-200', 200, '+237650000001', 'MOBILE_MONEY', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
