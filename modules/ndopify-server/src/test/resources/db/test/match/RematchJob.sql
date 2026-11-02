-- Re-matched: 1 (tenant 1) and 4 (tenant 2), APPROVED searches with an agent
-- Left out: 2 (PENDING_APPROVAL), 3 (no agent), 5 (of an inactive tenant)
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', true),
    (3, 'Inactive', 'inactive.localhost', 'fr-CM, en-CM', 'CM', false);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name, latitude, longitude) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde', 3.8667, 11.5167);

-- 100, 120, 130: search customers   101, 121, 131: owners   200, 220, 230: agents
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'owner@gmail.com', 'Owen', 'Er'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (120, 2, 'other.customer@gmail.com', 'Oth', 'Er'),
    (121, 2, 'other.owner@gmail.com', 'Oth', 'Owner'),
    (220, 2, 'other.agent@gmail.com', 'Oth', 'Agent'),
    (130, 3, 'inactive.customer@gmail.com', 'In', 'Active'),
    (131, 3, 'inactive.owner@gmail.com', 'In', 'Owner'),
    (230, 3, 'inactive.agent@gmail.com', 'Ina', 'Agent');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101),
    (12, 2, 120),
    (13, 2, 121),
    (14, 3, 130),
    (15, 3, 131);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (22, 2, 220, 2370201),
    (23, 3, 230, 2370201);

-- 1..5: the searches. 11..13, 21, 31: the listings' mandates
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id, price, price_periodicity_type, currency_code) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'MONTHLY', 'XAF'),
    (2, 1, 10, 20, 'PENDING_APPROVAL', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'MONTHLY', 'XAF'),
    (3, 1, 10, NULL, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'MONTHLY', 'XAF'),
    (4, 2, 12, 22, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'MONTHLY', 'XAF'),
    (5, 3, 14, 23, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'MONTHLY', 'XAF'),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'MONTHLY', 'XAF'),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'MONTHLY', 'USD'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'MONTHLY', 'XAF'),
    (21, 2, 13, 22, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'MONTHLY', 'XAF'),
    (31, 3, 15, 23, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'MONTHLY', 'XAF');

-- 101: matches, never matched (its run was lost)   102: no longer matches (another currency)   103: matches, dismissed
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, price, currency_code, price_periodicity_type, title) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'MONTHLY', 'A'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'USD', 'MONTHLY', 'B'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'MONTHLY', 'C'),
    (201, 2, 21, 22, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'MONTHLY', 'Other'),
    (301, 3, 31, 23, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'MONTHLY', 'Inactive');

INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price) VALUES
    (1, 1, 1, 102, 20, 'ACTIVE', 0.9, 240000),
    (2, 1, 1, 103, 20, 'DISMISSED', 0.9, 240000);
