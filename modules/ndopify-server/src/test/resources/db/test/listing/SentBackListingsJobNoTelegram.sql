INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active, partner_central_url) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true, 'https://partner.ndopify.com');

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
    (2, 1, 10, 20, 'APPROVED', 'LISTING', 2370201);

-- Already UNDER_VERIFICATION: the job selects that status, and these tests call it directly rather than
-- POST /v1/listings/{id}/verify. 100: at the neighbourhood's centre (valid)   101: 30 km away (outside)
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, status, city_id, neighborhood_id, latitude, longitude, modified_at) VALUES
    (100, 1, 1, 20, 'UNDER_VERIFICATION', 2370201, 111, 3.8925339696406587, 11.511400038328873, '2026-01-01'),
    (101, 1, 2, 20, 'UNDER_VERIFICATION', 2370201, 111, 4.17, 11.53, '2026-01-01');

-- Every section but PHOTOS (counted from the images) and REMARKS (optional)
INSERT INTO T_LISTING_SECTION(tenant_id, listing_id, section, verified_at) VALUES
    (1, 100, 'LOCATION', now()), (1, 100, 'GPS', now()), (1, 100, 'PROPERTY', now()),
    (1, 100, 'AMENITIES', now()), (1, 100, 'TERMS', now()),
    (1, 101, 'LOCATION', now()), (1, 101, 'GPS', now()), (1, 101, 'PROPERTY', now()),
    (1, 101, 'AMENITIES', now()), (1, 101, 'TERMS', now());

-- 5 photos each, already VERIFIED so that the verifier doesn't download them
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
    (1, 101, 'e', 'https://cdn.example.com/e', 'image/jpeg', 'VERIFIED');
