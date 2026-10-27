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
    (9, 2, 90, 90, 'APPROVED', 'LISTING', 2370201);

-- 100: draft   101: pending verification   900: other tenant
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, status, city_id, created_at, modified_at) VALUES
    (100, 1, 1, 20, 'DRAFT', 2370201, '2026-10-01 10:00:00', '2026-10-02 10:00:00'),
    (101, 1, 2, 20, 'PENDING_VERIFICATION', 2370201, '2026-10-01 10:00:00', '2026-10-02 10:00:00'),
    (900, 2, 9, 90, 'DRAFT', 2370201, '2026-10-01 10:00:00', '2026-10-02 10:00:00');
