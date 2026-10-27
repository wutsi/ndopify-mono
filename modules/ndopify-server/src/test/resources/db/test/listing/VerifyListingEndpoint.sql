-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (90, 2, 900);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (90, 2, 900, 2370201);

INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, city_id) VALUES
    (1, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (2, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (3, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (4, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (9, 2, 90, 90, 'APPROVED', 'LISTING', 2370201);

-- 100: complete   101: GPS left   102: a photo refused   103: already sent   900: other tenant, complete
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, status, city_id, modified_at) VALUES
    (100, 1, 1, 20, 'DRAFT', 2370201, '2026-01-01'),
    (101, 1, 2, 20, 'DRAFT', 2370201, '2026-01-01'),
    (102, 1, 3, 20, 'DRAFT', 2370201, '2026-01-01'),
    (103, 1, 4, 20, 'UNDER_VERIFICATION', 2370201, '2026-01-01'),
    (900, 2, 9, 90, 'DRAFT', 2370201, '2026-01-01');

-- Every section but PHOTOS (counted from the images) and REMARKS (optional); 101 without GPS
INSERT INTO T_LISTING_SECTION(tenant_id, listing_id, section, verified_at) VALUES
    (1, 100, 'LOCATION', now()), (1, 100, 'GPS', now()), (1, 100, 'PROPERTY', now()),
    (1, 100, 'AMENITIES', now()), (1, 100, 'TERMS', now()),
    (1, 101, 'LOCATION', now()), (1, 101, 'PROPERTY', now()), (1, 101, 'AMENITIES', now()), (1, 101, 'TERMS', now()),
    (1, 102, 'LOCATION', now()), (1, 102, 'GPS', now()), (1, 102, 'PROPERTY', now()),
    (1, 102, 'AMENITIES', now()), (1, 102, 'TERMS', now()),
    (2, 900, 'LOCATION', now()), (2, 900, 'GPS', now()), (2, 900, 'PROPERTY', now()),
    (2, 900, 'AMENITIES', now()), (2, 900, 'TERMS', now());

-- 5 photos each; 102's last one REJECTED
INSERT INTO T_LISTING_IMAGE(tenant_id, listing_id, path, url, mime_type, status) VALUES
    (1, 100, 'a', 'https://cdn.example.com/a', 'image/jpeg', 'PENDING_VERIFICATION'),
    (1, 100, 'b', 'https://cdn.example.com/b', 'image/jpeg', 'PENDING_VERIFICATION'),
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
    (1, 102, 'e', 'https://cdn.example.com/e', 'image/jpeg', 'REJECTED'),
    (2, 900, 'a', 'https://cdn.example.com/a', 'image/jpeg', 'VERIFIED'),
    (2, 900, 'b', 'https://cdn.example.com/b', 'image/jpeg', 'VERIFIED'),
    (2, 900, 'c', 'https://cdn.example.com/c', 'image/jpeg', 'VERIFIED'),
    (2, 900, 'd', 'https://cdn.example.com/d', 'image/jpeg', 'VERIFIED'),
    (2, 900, 'e', 'https://cdn.example.com/e', 'image/jpeg', 'VERIFIED');
