INSERT INTO T_ROLE(id, application_id, code) VALUES
  (1, 1, 'ADMIN'),
  (2, 1, 'SUPPORT');

INSERT INTO T_USER (id, email, deleted, created_at, deleted_at) VALUES
  (1, 'ray.sponsible@gmail.com', false, NOW(), NULL),
  (2, 'no-password@gmail.com', false, NOW(), NULL),
  (3, 'expired-password@gmail.com', false, NOW(), NULL);

INSERT INTO T_AUTH_FACTOR (id, user_id, auth_type, data, created_at, expires_at) VALUES
  (1, 1, 1, 'secret-password', NOW(), null),
  (3, 3, 1, 'secret-password', '2019-01-01 00:00:00', '2020-01-01 00:00:00');

INSERT INTO T_USER_APPLICATION (id, user_id, application_id) VALUES
  (11, 1, 1),
  (21, 2, 1),
  (31, 3, 1);

INSERT INTO T_USER_APPLICATION_ROLE (user_application_id, role_id) VALUES
  (11, 1),
  (11, 2);
