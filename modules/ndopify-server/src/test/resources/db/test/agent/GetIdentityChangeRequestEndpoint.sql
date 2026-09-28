INSERT INTO T_AGENT (
    id, tenant_id, user_id, agent_type, first_name, last_name,
    mobile_money_number, mobile_money_gateway, created_at, modified_at
) VALUES
  (1, 1, 1, 1, 'Ray', 'Sponsible', '+237600000000', 0, NOW(), NOW());

INSERT INTO T_IDENTITY_CHANGE (
    id, tenant_id, agent_id, old_first_name, old_last_name, new_first_name, new_last_name,
    identity_type, image_urls, holder_name, status, error_code, retries, created_at, verified_at
) VALUES (
    1, 1, 1, 'Ray', 'Sponsible', 'Ray', 'Sponsible',
    1, 'https://example.com/page1.png,https://example.com/page2.png', 'Ray Sponsible', 3, NULL, 0, NOW(), NOW()
);
