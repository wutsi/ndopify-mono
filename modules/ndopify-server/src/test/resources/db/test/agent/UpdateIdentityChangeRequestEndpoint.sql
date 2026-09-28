INSERT INTO T_AGENT (
    id, tenant_id, user_id, agent_type, first_name, last_name,
    mobile_money_number, mobile_money_gateway, created_at, modified_at
) VALUES
  (1, 1, 1, 1, 'Ray', 'Sponsible', '+237600000000', 0, NOW(), NOW());

INSERT INTO T_IDENTITY_CHANGE (
    id, tenant_id, agent_id, new_first_name, new_last_name, identity_type, status, retries, created_at
) VALUES
  (1, 1, 1, 'Ray', 'Sponsible', 1, 5, 0, NOW()),
  (2, 1, 1, 'Raymondo', 'Sponsible', 1, 5, 0, NOW()),
  (3, 1, 1, 'Ray', 'Sponsible', 1, 1, 0, NOW()),
  (4, 1, 1, 'Ray', 'Sponsible', 1, 5, 0, NOW());

UPDATE T_AGENT SET identity_change_id = 1 WHERE id = 1;
