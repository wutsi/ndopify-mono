INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, created_at
) VALUES
    (7201, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', '2000-01-01 09:00:00', NULL, '2000-01-01 09:00:00'), -- both passed: due
    (7202, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', '2099-01-01 09:00:00', NULL, '2000-01-01 09:00:00'), -- slot ahead
    (7203, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', '2000-01-01 09:00:00', NULL, NOW()),                 -- too recent
    (7204, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE', '2000-01-01 09:00:00', '2099-01-01 09:00:00', '2000-01-01 09:00:00');
