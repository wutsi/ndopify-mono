-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

-- Cities and neighborhoods referenced by the agents and mandates (T_AGENT, T_MANDATE and their neighborhoods)
INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde'),
    (2220957, NULL, 'CITY', 'CM', 'Douala', 'Douala'),
    (1514, NULL, 'CITY', 'CA', 'Montreal', 'Montreal'),
    (111, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos'),
    (222, 2370201, 'NEIGHBORHOOD', 'CM', 'Mvan', 'Mvan'),
    (333, 2370201, 'NEIGHBORHOOD', 'CM', 'Nlongkak', 'Nlongkak'),
    (444, 2370201, 'NEIGHBORHOOD', 'CM', 'Essos', 'Essos'),
    (555, 2370201, 'NEIGHBORHOOD', 'CM', 'Biyem-Assi', 'Biyem-Assi'),
    (666, 2370201, 'NEIGHBORHOOD', 'CM', 'Mendong', 'Mendong');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101),
    (90, 2, 900);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 101, 2370201);

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (1, 1, 10, null, 'PENDING_ASSIGNMENT', 'TENANT', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 1, 10, 20,   'ASSIGNED', 'OWNER', 'SALE', 2370201, 'VILLA', '4', 90000000, 'NONE', 'XAF', '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (3, 1, 11, 20,   'PUBLISHED', 'TENANT', 'RENTAL', 2220957, 'ROOM', '1', 50000, 'MONTHLY', 'XAF', '2024-06-12 00:00:00', '2024-06-12 00:00:00'),
    (4, 2, 90, null, 'PENDING_ASSIGNMENT', 'TENANT', 'RENTAL', 2370201, 'APARTMENT', '2', 100000, 'MONTHLY', 'XAF', '2024-06-13 00:00:00', '2024-06-13 00:00:00');

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES
    (1, 111),
    (1, 222),
    (2, 222),
    (3, 333);
