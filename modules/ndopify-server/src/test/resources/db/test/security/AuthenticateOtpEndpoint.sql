INSERT INTO T_TENANT(id, active, name, domain_name, locales, country_code, number_format, currency_code, currency_symbol, monetary_format, date_format, time_format, date_time_format, logo_url)
VALUES
    (1, true, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', '#,###,##0.00', 'XAF', 'FCFA', '#,###,##0 FCFA', 'yyyy-MM-dd', 'HH:mm', 'yyyy-MM-dd HH:mm', 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/logo.png');

-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_ROLE(id, application_id, code) VALUES
  (31, 3, 'AGENT');


-- 1: valid OTP ("123456")        2: expired OTP            3: password only, never asked for an OTP
-- 4: valid OTP, no access to app 5: no factor yet (full flow)
-- 6: expired OTP, issued after a previous login and never used
-- 7: valid OTP, member of tenant 2 only
INSERT INTO T_USER (id, email, deleted, created_at, deleted_at) VALUES
  (1, 'ray.sponsible@gmail.com', false, NOW(), NULL),
  (2, 'expired-otp@gmail.com', false, NOW(), NULL),
  (3, 'no-otp@gmail.com', false, NOW(), NULL),
  (4, 'no-access@gmail.com', false, NOW(), NULL),
  (5, 'flow@gmail.com', false, NOW(), NULL),
  (6, 'expired-after-login@gmail.com', false, NOW(), NULL),
  (7, 'other-tenant@gmail.com', false, NOW(), NULL);

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id) VALUES
  (1, 1, 1),
  (2, 1, 2),
  (3, 1, 3),
  (4, 1, 4),
  (5, 1, 5),
  (6, 1, 6),
  (7, 2, 7);

-- auth_type: 1=PASSWORD, 3=OTP. data = md5("<code>-<salt>"), same as PasswordEncryptor.
INSERT INTO T_AUTH_FACTOR (id, user_id, auth_type, data, salt, created_at, modified_at, expires_at, last_logged_in_at) VALUES
  (1, 1, 3, '123456-test-salt', 'test-salt', NOW(), NOW(), '2099-01-01 00:00:00', NULL),
  (2, 2, 3, '123456-test-salt', 'test-salt', '2019-01-01 00:00:00', '2019-01-01 00:00:00', '2020-01-01 00:00:00', NULL),
  (3, 3, 1, 'secret-password', NULL, NOW(), NOW(), NULL, NULL),
  (4, 4, 3, '123456-test-salt', 'test-salt', NOW(), NOW(), '2099-01-01 00:00:00', NULL),
  (6, 6, 3, '123456-test-salt', 'test-salt', '2019-06-01 00:00:00', '2019-06-01 00:00:00', '2020-01-01 00:00:00', '2019-01-01 00:00:00'),
  (7, 7, 3, '123456-test-salt', 'test-salt', NOW(), NOW(), '2099-01-01 00:00:00', NULL);

INSERT INTO T_USER_APPLICATION (id, membership_id, application_id) VALUES
  (11, 1, 3),
  (21, 2, 3),
  (31, 3, 3),
  (51, 5, 3),
  (61, 6, 3),
  (71, 7, 3);

INSERT INTO T_USER_APPLICATION_ROLE (user_application_id, role_id) VALUES
  (11, 31),
  (51, 31),
  (71, 31);
