INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active, public_portal_url) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true, 'https://www.ndopify.com');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaoundé', 'Yaounde'),
    (111, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201);

INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, city_id) VALUES
    (1, 1, 10, 20, 'APPROVED', 'LISTING', 2370201);

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, status, city_id, neighborhood_id, retries, modified_at) VALUES
    (100, 1, 1, 20, 'PUBLISHING', 2370201, 111, 0, '2026-01-01');
