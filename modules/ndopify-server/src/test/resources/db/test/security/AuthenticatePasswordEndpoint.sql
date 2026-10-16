-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_ROLE(id, application_id, code) VALUES
  (1, 1, 'ADMIN'),
  (2, 1, 'SUPPORT');

INSERT INTO T_USER (id, email, deleted, created_at, deleted_at) VALUES
  (1, 'ray.sponsible@gmail.com', false, NOW(), NULL),
  (2, 'no-password@gmail.com', false, NOW(), NULL),
  (3, 'expired-password@gmail.com', false, NOW(), NULL),
  (4, 'other-tenant@gmail.com', false, NOW(), NULL);

-- 1: member of tenants 1 and 2, with different roles     4: member of tenant 2 only, while the tests run on tenant 1
INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id) VALUES
  (1, 1, 1),
  (2, 1, 2),
  (3, 1, 3),
  (4, 2, 4),
  (5, 2, 1);

INSERT INTO T_AUTH_FACTOR (id, user_id, auth_type, data, created_at, expires_at) VALUES
  (1, 1, 'PASSWORD', 'secret-password', NOW(), null),
  (3, 3, 'PASSWORD', 'secret-password', '2019-01-01 00:00:00', '2020-01-01 00:00:00'),
  (4, 4, 'PASSWORD', 'secret-password', NOW(), null);

INSERT INTO T_USER_APPLICATION (id, membership_id, application_id) VALUES
  (11, 1, 1),
  (21, 2, 1),
  (31, 3, 1),
  (41, 4, 1),
  (51, 5, 1);

INSERT INTO T_USER_APPLICATION_ROLE (user_application_id, role_id) VALUES
  (11, 1),
  (11, 2),
  (51, 2);
