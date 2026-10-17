INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'john.smith@gmail.com', 'John', 'Smith'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

-- Sequences that already exist: BAIL this year for tenant 1, last year's BAIL, and MNDT this year for tenant 2
INSERT INTO T_CONTRACT_SEQUENCE(tenant_id, prefix, year, value) VALUES
    (1, 'BAIL', YEAR(NOW()), 41),
    (1, 'BAIL', YEAR(NOW()) - 1, 500),
    (2, 'MNDT', YEAR(NOW()), 99);
