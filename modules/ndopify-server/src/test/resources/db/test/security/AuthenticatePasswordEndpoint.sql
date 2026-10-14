-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_ROLE(id, application_id, code) VALUES
  (1, 1, 'ADMIN'),
  (2, 1, 'SUPPORT');

-- 4: home tenant 2, while the tests run on tenant 1
INSERT INTO T_USER (id, tenant_id, email, deleted, created_at, deleted_at) VALUES
  (1, NULL, 'ray.sponsible@gmail.com', false, NOW(), NULL),
  (2, NULL, 'no-password@gmail.com', false, NOW(), NULL),
  (3, NULL, 'expired-password@gmail.com', false, NOW(), NULL),
  (4, 2, 'other-tenant@gmail.com', false, NOW(), NULL);

INSERT INTO T_AUTH_FACTOR (id, user_id, auth_type, data, created_at, expires_at) VALUES
  (1, 1, 1, 'secret-password', NOW(), null),
  (3, 3, 1, 'secret-password', '2019-01-01 00:00:00', '2020-01-01 00:00:00'),
  (4, 4, 1, 'secret-password', NOW(), null);

INSERT INTO T_USER_APPLICATION (id, user_id, application_id) VALUES
  (11, 1, 1),
  (21, 2, 1),
  (31, 3, 1),
  (41, 4, 1);

INSERT INTO T_USER_APPLICATION_ROLE (user_application_id, role_id) VALUES
  (11, 1),
  (11, 2);
