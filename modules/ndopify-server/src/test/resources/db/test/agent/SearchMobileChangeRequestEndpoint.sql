INSERT INTO T_AGENT (id, tenant_id, user_id, agent_type, first_name, last_name, city_id, status, created_at, modified_at) VALUES
  (1, 1, 1, 1, 'Ray', 'Sponsible', 2370201, 1, NOW(), NOW()),
  (2, 1, 2, 1, 'Kim', 'Possible', 2370201, 1, NOW(), NOW());

INSERT INTO T_MOBILE_CHANGE (
    id, tenant_id, agent_id, old_mobile_number, new_mobile_number, new_gateway, status, created_at
) VALUES
  (1, 1, 1, NULL, '+237611111111', 1, 1, NOW()),
  (2, 1, 1, '+237611111111', '+237622222222', 1, 3, NOW()),
  (3, 1, 2, NULL, '+237633333333', 2, 1, NOW());
