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
    (9, 2, 90, 90, 'APPROVED', 'LISTING', 2370201);

-- 100: verified, with retries left by its verification   101: not verified yet   102: already published
-- 900: other tenant, verified
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, status, city_id, retries, modified_at) VALUES
    (100, 1, 1, 20, 'VERIFIED', 2370201, 2, '2026-01-01'),
    (101, 1, 2, 20, 'UNDER_VERIFICATION', 2370201, NULL, '2026-01-01'),
    (102, 1, 3, 20, 'PUBLISHED', 2370201, NULL, '2026-01-01'),
    (900, 2, 9, 90, 'VERIFIED', 2370201, NULL, '2026-01-01');
