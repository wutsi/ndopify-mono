INSERT INTO T_TENANT(id, active, name, domain_name, locales, country_code, number_format, currency_code, currency_symbol, monetary_format, date_format, time_format, date_time_format, logo_url)
VALUES
    (1, true, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', '#,###,##0.00', 'XAF', 'FCFA', '#,###,##0 FCFA', 'yyyy-MM-dd', 'HH:mm', 'yyyy-MM-dd HH:mm', 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/logo.png'),
    (2, true, 'Autre Marque', 'other.localhost', 'fr-CM', 'CM', '#,###,##0.00', 'XAF', 'FCFA', '#,###,##0 FCFA', 'yyyy-MM-dd', 'HH:mm', 'yyyy-MM-dd HH:mm', 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/other-logo.png');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
  (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', NOW(), NOW());

-- user 1: has a party (display name), no home tenant
-- user 2: already has an OTP and a password
-- user 3: home tenant 2
INSERT INTO T_USER (id, tenant_id, party_id, email, deleted, created_at, deleted_at) VALUES
  (1, NULL, 100, 'ray.sponsible@gmail.com', false, NOW(), NULL),
  (2, NULL, NULL, 'has-otp@gmail.com', false, NOW(), NULL),
  (3, 2, NULL, 'home-tenant@gmail.com', false, NOW(), NULL);

-- auth_type: 1=PASSWORD, 3=OTP
INSERT INTO T_AUTH_FACTOR (id, user_id, auth_type, data, salt, created_at, modified_at, expires_at) VALUES
  (21, 2, 1, 'secret-password', NULL, '2019-01-01 00:00:00', '2019-01-01 00:00:00', NULL),
  (22, 2, 3, 'old-otp-hash', 'old-salt', '2019-01-01 00:00:00', '2019-01-01 00:00:00', '2020-01-01 00:00:00');
