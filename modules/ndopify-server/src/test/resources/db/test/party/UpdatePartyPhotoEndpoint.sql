-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Google', 'google.ca', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 'john.smith@gmail.com', 'John', 'Smith', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- The token's user 11 is party 100; user 12 is party 101
INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-05-20 00:00:00'),
    (12, 'john.smith@gmail.com', false, '2024-05-20 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false),
    (12, 1, 12, 101, false);
