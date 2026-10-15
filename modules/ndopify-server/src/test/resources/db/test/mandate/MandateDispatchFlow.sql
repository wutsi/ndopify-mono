INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, time_zone, partner_central_url) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'Africa/Douala', 'http://localhost:8081/partner-central');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 3, 'CM', 'Yaounde', 'Yaounde');

-- 100: customer   200: agent 20 (user 11, the caller)   300: agent 30
-- kyc_status: 4=VERIFIED
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, kyc_status) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', 0),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible', 4),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe', 4);

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201);

-- The tests run at 2026-10-15 12:00 (Douala), see MandateDispatchFlowTest.NOW
-- status: 1=PENDING_ASSIGNMENT, 2=ASSIGNED
-- 1: offered to agent 20   2: offered to agent 20, past its TTL   3: waiting, never offered   4: assigned to agent 20
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (1, 1, 10, null, 1, 1, 2, 2370201, 3, '2', 150000, 4, 'XAF', '2026-10-15 00:00:00', '2026-10-15 00:00:00'),
    (2, 1, 10, null, 1, 1, 2, 2370201, 3, '2', 150000, 4, 'XAF', '2026-10-15 00:00:00', '2026-10-15 00:00:00'),
    (3, 1, 10, null, 1, 1, 2, 2370201, 3, '2', 150000, 4, 'XAF', '2026-10-15 00:00:00', '2026-10-15 00:00:00'),
    (4, 1, 10, 20, 2, 1, 2, 2370201, 3, '2', 150000, 4, 'XAF', '2026-10-15 00:00:00', '2026-10-15 00:00:00');

-- status: 1=PENDING
INSERT INTO T_ASSIGNMENT(id, tenant_id, mandate_id, agent_id, status, score, created_at, modified_at, expires_at) VALUES
    (1, 1, 1, 20, 1, 0.8, '2026-10-15 09:00:00', '2026-10-15 09:00:00', '2026-10-15 23:00:00'),
    (2, 1, 2, 20, 1, 0.8, '2026-10-15 03:00:00', '2026-10-15 03:00:00', '2026-10-15 05:00:00');
