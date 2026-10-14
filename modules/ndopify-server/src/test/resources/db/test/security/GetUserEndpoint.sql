-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (901, 2, 'ray.sponsible@gmail.com', 'Raymond', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 1: with a party in each of tenants 1 and 2   2: no party   3: deleted   4: member of another tenant only
-- 5: membership deleted
INSERT INTO T_USER (id, email, deleted, created_at, deleted_at) VALUES
  (1, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00', NULL),
  (2, 'admin@gmail.com', false, '2024-06-10 00:00:00', NULL),
  (3, 'deleted@gmail.com', true, '2024-06-10 00:00:00', NOW()),
  (4, 'other.tenant@example.com', false, '2024-06-10 00:00:00', NULL),
  (5, 'former.member@gmail.com', false, '2024-06-10 00:00:00', NULL);

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
  (1, 1, 1, 100, false),
  (2, 1, 2, NULL, false),
  (3, 1, 3, NULL, false),
  (4, 2, 4, 900, false),
  (5, 1, 5, NULL, true),
  (6, 2, 1, 901, false);
