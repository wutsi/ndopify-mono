INSERT INTO T_AGENT (
    id, tenant_id, user_id, agent_type, first_name, last_name,
    mobile_money_number, mobile_money_gateway, mobile_money_kyc_status, created_at, modified_at
) VALUES
  (1, 1, 1, 1, 'Ray', 'Sponsible', '+237600000000', 0, 1, NOW(), NOW());

INSERT INTO T_MOBILE_CHANGE (
    id, tenant_id, agent_id, verify_by_user_id, old_mobile_number, new_mobile_number, new_gateway, status, retries, created_at
) VALUES
  (1, 1, 1, NULL, '+237600000000', '+237600000000', 1, 5, 0, NOW()),
  (2, 1, 1, NULL, NULL, '+237600000001', 1, 5, 0, NOW()),
  (3, 1, 1, NULL, NULL, '+237600000001', 1, 1, 0, NOW()),
  (4, 1, 1, NULL, NULL, '+237600000001', 1, 5, 0, NOW());

update T_AGENT set mobile_change_id = 1 where id = 1;
