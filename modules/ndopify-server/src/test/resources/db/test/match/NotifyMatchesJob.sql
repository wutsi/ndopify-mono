-- Agent 20 (tenant 1): matches 1 and 2 to send, on 2 mandates. Agent 21 (tenant 1): match 3 to send.
-- Not sent: 4 already notified, 5 DISMISSED, 6 mandate CLOSED, 7 listing RENTED, 31 the inactive tenant's
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true),
    (3, 'Inactive', 'inactive.localhost', 'fr-CM, en-CM', 'CM', false);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name, latitude, longitude) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde', 3.8667, 11.5167);

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'owner@gmail.com', 'Owen', 'Er'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (210, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (300, 3, 'inactive.customer@gmail.com', 'In', 'Active'),
    (310, 3, 'inactive.agent@gmail.com', 'Ina', 'Agent');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101),
    (30, 3, 300);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (21, 1, 210, 2370201),
    (31, 3, 310, 2370201);

-- 1, 2: agent 20's searches; 3: agent 21's; 4: agent 20's, closed. 11..13: the listings' mandates. 30, 33: inactive tenant
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id, price, currency_code) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (2, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (3, 1, 10, 21, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (4, 1, 10, 20, 'CLOSED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (12, 1, 11, 21, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (30, 3, 30, 31, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (33, 3, 30, 31, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF');

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, price, currency_code, title) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'A'),
    (102, 1, 12, 21, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'B'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'RENTED', 2370201, 240000, 'XAF', 'C'),
    (301, 3, 33, 31, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'Inactive');

INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, notified_at) VALUES
    (1, 1, 1, 101, 20, 'ACTIVE', 0.9, 240000, NULL),
    (2, 1, 2, 101, 20, 'ACTIVE', 0.8, 240000, NULL),
    (3, 1, 3, 102, 21, 'ACTIVE', 0.7, 240000, NULL),
    (4, 1, 1, 102, 20, 'ACTIVE', 0.7, 240000, '2026-10-01 10:00:00'),
    (5, 1, 2, 102, 20, 'DISMISSED', 0.7, 240000, NULL),
    (6, 1, 4, 101, 20, 'ACTIVE', 0.7, 240000, NULL),
    (7, 1, 1, 103, 20, 'ACTIVE', 0.7, 240000, NULL),
    (31, 3, 30, 301, 31, 'ACTIVE', 0.9, 240000, NULL);
