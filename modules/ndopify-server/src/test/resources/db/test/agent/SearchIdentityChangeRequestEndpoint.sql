INSERT INTO T_AGENT (id, tenant_id, user_id, agent_type, first_name, last_name, city_id, status, created_at, modified_at) VALUES
  (1, 1, 1, 1, 'Ray', 'Sponsible', 2370201, 1, NOW(), NOW()),
  (2, 1, 2, 1, 'Kim', 'Possible', 2370201, 1, NOW(), NOW());

INSERT INTO T_IDENTITY_CHANGE (
    id, tenant_id, agent_id, new_first_name, new_last_name, identity_type, status, created_at
) VALUES
  (1, 1, 1, 'Ray', 'Sponsible', 1, 1, NOW()),
  (2, 1, 1, 'Raymond', 'Sponsible', 1, 3, NOW()),
  (3, 1, 2, 'Kim', 'Possible', 2, 1, NOW());
