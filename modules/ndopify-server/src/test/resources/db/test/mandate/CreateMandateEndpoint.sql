-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

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
    (100, 1, '007@gmail.com', 'James', 'Bond'),
    (101, 1, 'omam.mbiyick@gmail.com', 'Omam', 'Mbiyick');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (101, 1, 101);

-- The agent of the listings a visitor asks about (500, 501), and the LISTING mandates they belong to
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (300, 1, 'listing.agent@gmail.com', 'Listing', 'Agent');

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (30, 1, 300, 2370201);

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, property_category, status, mandate_type, listing_type, city_id,
    price, price_periodicity_type, currency_code
) VALUES
    (400, 1, 101, 30, 'RESIDENTIAL', 'APPROVED', 'LISTING', 'RENTAL', 2370201, 175000, 'MONTHLY', 'XAF'),
    (401, 1, 101, 30, 'RESIDENTIAL', 'CLOSED', 'LISTING', 'RENTAL', 2370201, 175000, 'MONTHLY', 'XAF');

-- 500: published apartment in Bastos   501: the same, sold
INSERT INTO T_LISTING(
    id, tenant_id, version, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, furniture_type, rooms, parkings, area, water_tank,
    price, currency_code, price_periodicity_type, title, url, created_at, modified_at, published_at
) VALUES
    (500, 1, 0, 400, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 111,
     'APARTMENT', 'FULLY_FURNISHED', 2, 1, 75, true,
     175000, 'XAF', 'MONTHLY', 'Appartement meublé 2 chambres',
     '/yaounde/bastos/listing/500/appartement-meuble', '2026-10-01 10:00:00', '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (501, 1, 0, 401, 30, 'RESIDENTIAL', 'RENTAL', 'SOLD', 2370201, 111,
     'APARTMENT', 'FURNISHED', 2, 1, 75, true,
     175000, 'XAF', 'MONTHLY', 'Appartement vendu',
     '/yaounde/bastos/listing/501/appartement-vendu', '2026-10-01 10:00:00', '2026-10-01 10:00:00', '2026-10-01 10:00:00');
