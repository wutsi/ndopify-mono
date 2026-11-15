-- Tenants referenced by the tenant-scoped rows below: only tenant 1 has a Partner Central URL
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, partner_central_url) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'https://npc.example'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', NULL);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

-- 100: tenant 1's customer   200: agent 20   900: tenant 2's agent
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, photo_url) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', NULL),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible', 'https://cdn.ndopify.com/agents/200.jpg'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', NULL);

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (90, 2, 900);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id, whatsapp_number, biography) VALUES
    (20, 1, 200, 2370201, '+237670000020', 'Agent immobilier à Yaoundé depuis 10 ans'),
    (90, 2, 900, 2370201, null, null);

-- The chats linked to the bot: 1000 is agent 20 (tenant 1), 2000 is agent 90 (tenant 2)
INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES
    (1, 20, 1000, '2024-06-10 00:00:00'),
    (2, 90, 2000, '2024-06-10 00:00:00');

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (1, 1, 10, null, 'PENDING_ASSIGNMENT', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (4, 1, 10, null, 'PENDING_ASSIGNMENT', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (9, 2, 90, null, 'PENDING_ASSIGNMENT', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 1: pending for agent 20   4: pending for agent 20, past its TTL   9: pending for agent 90 (tenant 2)
INSERT INTO T_ASSIGNMENT(id, tenant_id, mandate_id, agent_id, status, score, created_at, modified_at, expires_at) VALUES
    (1, 1, 1, 20, 'PENDING', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00'),
    (4, 1, 4, 20, 'PENDING', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2020-01-01 00:00:00'),
    (9, 2, 9, 90, 'PENDING', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00');
