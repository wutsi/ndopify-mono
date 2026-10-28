INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name, latitude, longitude) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaoundé', 'Yaounde', 3.8667, 11.5167),
    (111, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos', 3.8925339696406587, 11.511400038328873),
    (112, 2370201, 'NEIGHBORHOOD', 'CM', 'Far Away', 'Far-Away', 4.17, 11.53);

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201);

INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, city_id) VALUES
    (1, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (2, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (3, 1, 10, 20, 'APPROVED', 'LISTING', 2370201);

-- All DRAFT and ready to be sent, in Bastos. 100: at its center   101: 30 km away (in Far Away)   102: like 100, with
-- a photo that can't be downloaded
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, status, city_id, neighborhood_id, latitude, longitude, modified_at) VALUES
    (100, 1, 1, 20, 'DRAFT', 2370201, 111, 3.8925339696406587, 11.511400038328873, '2026-01-01'),
    (101, 1, 2, 20, 'DRAFT', 2370201, 111, 4.17, 11.53, '2026-01-01'),
    (102, 1, 3, 20, 'DRAFT', 2370201, 111, 3.8925339696406587, 11.511400038328873, '2026-01-01');

-- Every section but PHOTOS (counted from the images) and REMARKS (optional)
INSERT INTO T_LISTING_SECTION(tenant_id, listing_id, section, verified_at) VALUES
    (1, 100, 'LOCATION', now()), (1, 100, 'GPS', now()), (1, 100, 'PROPERTY', now()),
    (1, 100, 'AMENITIES', now()), (1, 100, 'TERMS', now()),
    (1, 101, 'LOCATION', now()), (1, 101, 'GPS', now()), (1, 101, 'PROPERTY', now()),
    (1, 101, 'AMENITIES', now()), (1, 101, 'TERMS', now()),
    (1, 102, 'LOCATION', now()), (1, 102, 'GPS', now()), (1, 102, 'PROPERTY', now()),
    (1, 102, 'AMENITIES', now()), (1, 102, 'TERMS', now());

-- 5 photos each, already VERIFIED so that the photos aren't downloaded; 102's last one PENDING_VERIFICATION, at a
-- port nothing listens on: its download fails at once
INSERT INTO T_LISTING_IMAGE(tenant_id, listing_id, path, url, mime_type, status) VALUES
    (1, 100, 'a', 'https://cdn.example.com/a', 'image/jpeg', 'VERIFIED'),
    (1, 100, 'b', 'https://cdn.example.com/b', 'image/jpeg', 'VERIFIED'),
    (1, 100, 'c', 'https://cdn.example.com/c', 'image/jpeg', 'VERIFIED'),
    (1, 100, 'd', 'https://cdn.example.com/d', 'image/jpeg', 'VERIFIED'),
    (1, 100, 'e', 'https://cdn.example.com/e', 'image/jpeg', 'VERIFIED'),
    (1, 101, 'a', 'https://cdn.example.com/a', 'image/jpeg', 'VERIFIED'),
    (1, 101, 'b', 'https://cdn.example.com/b', 'image/jpeg', 'VERIFIED'),
    (1, 101, 'c', 'https://cdn.example.com/c', 'image/jpeg', 'VERIFIED'),
    (1, 101, 'd', 'https://cdn.example.com/d', 'image/jpeg', 'VERIFIED'),
    (1, 101, 'e', 'https://cdn.example.com/e', 'image/jpeg', 'VERIFIED'),
    (1, 102, 'a', 'https://cdn.example.com/a', 'image/jpeg', 'VERIFIED'),
    (1, 102, 'b', 'https://cdn.example.com/b', 'image/jpeg', 'VERIFIED'),
    (1, 102, 'c', 'https://cdn.example.com/c', 'image/jpeg', 'VERIFIED'),
    (1, 102, 'd', 'https://cdn.example.com/d', 'image/jpeg', 'VERIFIED'),
    (1, 102, 'e', 'http://localhost:1/e.jpg', 'image/jpeg', 'PENDING_VERIFICATION');
