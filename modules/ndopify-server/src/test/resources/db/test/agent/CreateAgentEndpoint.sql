INSERT INTO T_USER (id, email, deleted, created_at, deleted_at) VALUES
  (1, 'agent-owner@gmail.com', false, NOW(), NULL);

INSERT INTO T_LOCATION (id, parent_fk, type, country, name, ascii_name) VALUES
  (2370201, null, 3, 'CM', 'Bafoussam', 'bafoussam');
