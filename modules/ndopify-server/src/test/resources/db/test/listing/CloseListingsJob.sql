INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201);

-- 1: closed, for sale   2: closed, for rent   3: cancelled   4: still approved   5: closed, its listing never published
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, closed_at) VALUES
    (1, 1, 10, 20, 'CLOSED', 'LISTING', 'SALE', 2370201, '2026-10-01'),
    (2, 1, 10, 20, 'CLOSED', 'LISTING', 'RENTAL', 2370201, '2026-10-01'),
    (3, 1, 10, 20, 'CANCELLED', 'LISTING', 'RENTAL', 2370201, '2026-10-01'),
    (4, 1, 10, 20, 'APPROVED', 'LISTING', 'RENTAL', 2370201, null),
    (5, 1, 10, 20, 'CLOSED', 'LISTING', 'RENTAL', 2370201, '2026-10-01');

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, listing_type, status, city_id, modified_at) VALUES
    (101, 1, 1, 20, 'SALE', 'PUBLISHED', 2370201, '2026-01-01'),
    (102, 1, 2, 20, 'RENTAL', 'PUBLISHED', 2370201, '2026-01-01'),
    (103, 1, 3, 20, 'RENTAL', 'PUBLISHED', 2370201, '2026-01-01'),
    (104, 1, 4, 20, 'RENTAL', 'PUBLISHED', 2370201, '2026-01-01'),
    (105, 1, 5, 20, 'RENTAL', 'DRAFT', 2370201, '2026-01-01');
