INSERT INTO T_TENANT(id, active, name, domain_name, locales, number_format, currency_code, currency_symbol, monetary_format, date_format, time_format, date_time_format, logo_url, icon_url, country_code)
    VALUES (1, true, 'test', 'localhost', 'en_CA,fr_CA', '#,###,###', 'CAD', 'CA$', 'CA$ #,###,###.#0', 'yyyy-MM-dd', 'hh:mm a', 'yyyy-MM-dd hh:mm a', NULL, NULL, 'CM');

INSERT INTO T_AGENT (
    id, tenant_id, user_id, agent_type, first_name, last_name,
    mobile_money_number, mobile_money_gateway, mobile_money_kyc_status, created_at, modified_at
) VALUES
  (1, 1, 1, 1, 'Ray', 'Sponsible', '+237600000000', 0, 1, NOW(), NOW()),
  (2, 1, 2, 1, 'Ray', 'Ray', '+237600000002', 0, 1, NOW(), NOW()),
  (3, 1, 3, 1, 'Kim', 'Possible', '+33612345678', 0, 1, NOW(), NOW());

INSERT INTO T_MOBILE_CHANGE (
    id, tenant_id, agent_id, verify_by_user_id, old_mobile_number, new_mobile_number, new_gateway, status, retries, created_at
) VALUES
  (1, 1, 1, 100, NULL, '+237600000000', 2, 1, 0, NOW()),
  (2, 1, 1, 100, NULL, '+237600000000', 1, 1, 0, NOW()),
  (3, 1, 1, 100, NULL, '+237600000000', 1, 3, 0, NOW()),
  (4, 1, 2, 100, '+237600000002', '+237600000002', 1, 1, 0, NOW()),
  (5, 1, 1, 100, NULL, '+237600000000', 1, 1, 0, NOW()),
  (6, 1, 1, 100, NULL, '+237600000000', 1, 1, 0, NOW()),
  (7, 1, 3, 100, NULL, '+33612345678', 1, 1, 0, NOW()),
  (8, 1, 1, 100, NULL, '+237600000000', 1, 1, 0, NOW()),
  (9, 1, 1, 100, NULL, '+237600000000', 1, 1, 3, NOW());
