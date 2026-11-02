-- To expire: 1: proposed 8 days ago; 21: of tenant 2, proposed 30 days ago
-- To keep: 2: proposed 6 days ago; 3: accepted; 4: rejected; 5: active; 6: already expired; 31: of an inactive tenant
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', true),
    (3, 'Inactive', 'inactive.localhost', 'fr-CM, en-CM', 'CM', false);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name, latitude, longitude) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde', 3.8667, 11.5167);

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (120, 2, 'other.customer@gmail.com', 'Oth', 'Er'),
    (220, 2, 'other.agent@gmail.com', 'Oth', 'Agent'),
    (130, 3, 'inactive.customer@gmail.com', 'In', 'Active'),
    (230, 3, 'inactive.agent@gmail.com', 'Ina', 'Agent');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (12, 2, 120),
    (13, 3, 130);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (22, 2, 220, 2370201),
    (23, 3, 230, 2370201);

INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id, price, currency_code) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (2, 2, 12, 22, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (3, 3, 13, 23, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (11, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (12, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (13, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (14, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (15, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (16, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (17, 2, 12, 22, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (18, 3, 13, 23, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF');

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, price, currency_code, title) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'A'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'B'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'C'),
    (104, 1, 14, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'D'),
    (105, 1, 15, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'E'),
    (106, 1, 16, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'F'),
    (201, 2, 17, 22, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'Other'),
    (301, 3, 18, 23, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'Inactive');

INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, proposed_at, answered_at) VALUES
    (1, 1, 1, 101, 20, 'PROPOSED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 8 DAY), NULL),
    (2, 1, 1, 102, 20, 'PROPOSED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 6 DAY), NULL),
    (3, 1, 1, 103, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 29 DAY)),
    (4, 1, 1, 104, 20, 'REJECTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 29 DAY)),
    (5, 1, 1, 105, 20, 'ACTIVE', 0.9, 240000, NULL, NULL),
    (6, 1, 1, 106, 20, 'EXPIRED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY), NULL),
    (21, 2, 2, 201, 22, 'PROPOSED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY), NULL),
    (31, 3, 3, 301, 23, 'PROPOSED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY), NULL);
