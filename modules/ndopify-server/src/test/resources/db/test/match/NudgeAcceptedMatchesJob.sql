-- Nudge: 1 (tenant 1, accepted 3 days ago, no visit), 21 (tenant 2)
-- Keep: 2 (accepted 1 day ago), 3 (open visit), 4 (already nudged), 5 (listing rented), 6 (mandate closed),
--       7 (no agent e-mail and no chat: marked, no e-mail), 31 (inactive tenant)
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, partner_central_url, currency_code, monetary_format, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'https://partner.ndopify.com', 'XAF', '#,###,##0 FCFA', true),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', 'https://other.ndopify.com', 'XAF', '#,###,##0 FCFA', true),
    (3, 'Inactive', 'inactive.localhost', 'fr-CM, en-CM', 'CM', NULL, 'XAF', '#,###,##0 FCFA', false);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (201, 1, '', 'No', 'Mail'),
    (120, 2, 'other.customer@gmail.com', 'Oth', 'Er'),
    (220, 2, 'other.agent@gmail.com', 'Oth', 'Agent'),
    (130, 3, 'inactive.customer@gmail.com', 'In', 'Active'),
    (230, 3, 'inactive.agent@gmail.com', 'Ina', 'Agent');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100), (11, 1, 201), (12, 2, 120), (13, 3, 130);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201), (21, 1, 201, 2370201), (22, 2, 220, 2370201), (23, 3, 230, 2370201);

INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id, price, currency_code) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (2, 1, 11, 21, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (3, 1, 10, 20, 'CLOSED',   'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (4, 2, 12, 22, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (5, 3, 13, 23, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (14, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (15, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (16, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (17, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (18, 2, 12, 22, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (19, 3, 13, 23, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF');

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, price, currency_code, title) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'A'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'B'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'C'),
    (104, 1, 14, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'D'),
    (105, 1, 15, 20, 'RESIDENTIAL', 'RENTAL', 'RENTED',    2370201, 240000, 'XAF', 'E'),
    (106, 1, 16, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'F'),
    (107, 1, 17, 21, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'G'),
    (201, 2, 18, 22, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'Other'),
    (301, 3, 19, 23, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'Inactive');

INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, proposed_at, answered_at, agent_visit_nudged_at) VALUES
    (1,  1, 1, 101, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (2,  1, 1, 102, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY), NULL),
    (3,  1, 1, 103, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (4,  1, 1, 104, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)),
    (5,  1, 1, 105, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (6,  1, 3, 106, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (7,  1, 2, 107, 21, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (21, 2, 4, 201, 22, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (31, 3, 5, 301, 23, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL);

-- Match 3 has an open visit: no nudge
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, code, reminder_sent_at, created_at
) VALUES
    (1, 1, 3, 1, 103, 20, 10, 'REQUESTED', 'ON_SITE',
     DATE_ADD(UTC_TIMESTAMP(), INTERVAL 1 DAY), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 2 DAY), NULL, NULL,
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY));
