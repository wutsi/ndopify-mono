-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, '007@gmail.com', 'J', 'Bond', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, party_id, type, issuing_country_code, number, number_suffix, status, created_at, modified_at) VALUES
    ('id-100-a', 100, 'NATIONAL_ID', 'CM', '1234567890', '7890', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('id-100-b', 100, 'PASSPORT', 'CM', '9876543210', '3210', 'VERIFIED', '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    ('id-101-a', 101, 'NATIONAL_ID', 'US', '1112223334', '3334', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
