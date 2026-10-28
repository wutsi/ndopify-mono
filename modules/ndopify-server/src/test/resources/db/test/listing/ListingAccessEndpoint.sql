INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

-- 100: customer   200: agent 20 (user 11, the caller)   300: agent 30 (user 12)   400: not an agent (user 13)
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (400, 1, 'jane.doe@gmail.com', 'Jane', 'Doe');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'jane.doe@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false),
    (13, 1, 13, 400, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201);

-- 1, 2: with a listing, of agent 20   3: with a listing, of agent 30   4: without listing, of agent 20
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, city_id) VALUES
    (1, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (2, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (3, 1, 10, 30, 'APPROVED', 'LISTING', 2370201),
    (4, 1, 10, 20, 'ASSIGNED', 'LISTING', 2370201);

-- 100: published, of agent 20   101: draft, of agent 20   102: draft, of agent 30
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, status, city_id) VALUES
    (100, 1, 1, 20, 'PUBLISHED', 2370201),
    (101, 1, 2, 20, 'DRAFT', 2370201),
    (102, 1, 3, 30, 'DRAFT', 2370201);

-- 1000: verified   1001: rejected, of the published listing   1010: pending verification, of the draft
INSERT INTO T_LISTING_IMAGE(id, tenant_id, listing_id, path, url, mime_type, status, created_at) VALUES
    (1000, 1, 100, 'listings/100/images/a.jpg', 'https://cdn.example.com/listings/100/images/a.jpg', 'image/jpeg',
     'VERIFIED', '2026-10-01 10:00:00'),
    (1001, 1, 100, 'listings/100/images/b.jpg', 'https://cdn.example.com/listings/100/images/b.jpg', 'image/jpeg',
     'REJECTED', '2026-10-01 11:00:00'),
    (1010, 1, 101, 'listings/101/images/a.jpg', 'https://cdn.example.com/listings/101/images/a.jpg', 'image/jpeg',
     'PENDING_VERIFICATION', '2026-10-01 10:00:00');
