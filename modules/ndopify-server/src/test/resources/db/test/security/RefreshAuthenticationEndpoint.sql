INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_USER (id, email, deleted) VALUES (11, 'ray.sponsible@gmail.com', false);

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES (11, 1, 11, 100, false);

-- application 3 is partner-central (V1_3)
INSERT INTO T_USER_APPLICATION (id, membership_id, application_id) VALUES (11, 11, 3);
