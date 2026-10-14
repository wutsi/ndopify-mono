-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 1: with party   2: no party, no home tenant (cross-tenant)   3: deleted   4: another tenant
INSERT INTO T_USER (id, tenant_id, party_id, email, deleted, created_at, deleted_at) VALUES
  (1, 1, 100, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00', NULL),
  (2, NULL, NULL, 'admin@gmail.com', false, '2024-06-10 00:00:00', NULL),
  (3, 1, NULL, 'deleted@gmail.com', true, '2024-06-10 00:00:00', NOW()),
  (4, 2, 900, 'other.tenant@example.com', false, '2024-06-10 00:00:00', NULL);
