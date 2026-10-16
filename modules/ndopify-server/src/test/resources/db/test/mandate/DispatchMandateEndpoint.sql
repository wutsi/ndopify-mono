INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, partner_central_url) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'http://localhost:8081/partner-central'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', NULL);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 3, 'CM', 'Yaounde', 'Yaounde');

-- 100: customer   200: agent 20   300: agent 30   900: other tenant
-- kyc_status: 4=VERIFIED
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, kyc_status) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', 0),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible', 4),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe', 4),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', 4);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (90, 2, 900);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201),
    (90, 2, 900, 2370201);

-- status: 1=PENDING_ASSIGNMENT, 2=ASSIGNED
-- 1: already offered   3: waiting   4: assigned to agent 20, so agent 30 is the idle one   5: waiting for too long
-- 9: other tenant
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (1, 1, 10, null, 1, 1, 2, 2370201, 3, '2', 150000, 4, 'XAF', NOW(), NOW()),
    (3, 1, 10, null, 1, 1, 2, 2370201, 3, '2', 150000, 4, 'XAF', NOW(), NOW()),
    (4, 1, 10, 20, 2, 1, 2, 2370201, 3, '2', 150000, 4, 'XAF', NOW(), NOW()),
    (5, 1, 10, null, 1, 1, 2, 2370201, 3, '2', 150000, 4, 'XAF', '2020-01-01 00:00:00', '2020-01-01 00:00:00'),
    (9, 2, 90, null, 1, 1, 2, 2370201, 3, '2', 150000, 4, 'XAF', NOW(), NOW());

-- status: 1=PENDING
INSERT INTO T_ASSIGNMENT(id, tenant_id, mandate_id, agent_id, status, score, created_at, modified_at, expires_at) VALUES
    (1, 1, 1, 20, 1, 0.8, NOW(), NOW(), '2099-01-01 00:00:00');
