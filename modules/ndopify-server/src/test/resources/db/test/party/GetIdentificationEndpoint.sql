-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, tenant_id, party_id, type, issuing_country_code, number, number_suffix, status, created_at, modified_at) VALUES
    ('id-100', 1, 100, 'NATIONAL_ID', 'CM', '1234567890', '7890', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('id-900', 2, 900, 'NATIONAL_ID', 'CM', '0987654321', '4321', 'PENDING_VERIFICATION', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
