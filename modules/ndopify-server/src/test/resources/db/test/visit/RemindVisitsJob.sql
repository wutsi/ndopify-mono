-- Remind: 1 (in 12 h), 5 (tenant 2, in 12 h)
-- Keep: 2 (in 30 h), 3 (reminder already sent, as when confirmed less than 24 h before), 4 (cancelled)
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, code, reminder_sent_at, created_at
) VALUES
    (1, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE',
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 12 HOUR), '1111', NULL,
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)),
    (2, 1, 2, 1, 102, 20, 10, 'CONFIRMED', 'ON_SITE',
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 HOUR), '2222', NULL,
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)),
    (3, 1, 3, 1, 103, 20, 10, 'CONFIRMED', 'ON_SITE',
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 5 HOUR), '3333',
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)),
    (4, 1, 2, 1, 102, 20, 10, 'CANCELLED', 'ON_SITE',
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 12 HOUR), '4444', NULL,
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)),
    (5, 2, 9, 9, 901, 90, 90, 'CONFIRMED', 'ON_SITE',
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 12 HOUR), '5555', NULL,
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY));
