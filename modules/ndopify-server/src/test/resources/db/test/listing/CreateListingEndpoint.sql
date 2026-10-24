-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde'),
    (2220957, NULL, 'CITY', 'CM', 'Douala', 'Douala'),
    (111, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos'),
    (222, 2370201, 'NEIGHBORHOOD', 'CM', 'Mvan', 'Mvan');

-- 100: customer (owner)   200: agent 20   900: other tenant
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

-- 1: assigned, no listing yet   2: no agent   3: already has listing 300   9: other tenant
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, property_category, status, mandate_type, listing_type, city_id,
    property_type, furniture_type, rooms, parkings, area,
    water_meter, dedicated_water_meter, water_tank, water_well,
    electricity_meter, dedicated_electricity_meter, generator_set, solar_panel,
    road_paved, track_accessible_by_car, track_inaccessible_by_car,
    secured_fenced, security_guard, video_surveillance,
    price, price_periodicity_type, currency_code, security_deposit, advance_rent, lease_term, notice_period,
    available_from
) VALUES
    (1, 1, 10, 20, 'RESIDENTIAL', 'APPROVED', 'LISTING', 'RENTAL', 2370201,
     'APARTMENT', 'FULLY_FURNISHED', '2+', '1', 75,
     true, true, false, false,
     true, false, true, false,
     true, false, false,
     true, false, true,
     150000, 'MONTHLY', 'XAF', 2, 3, 12, 1,
     '2026-12-01'),
    (2, 1, 10, null, 'RESIDENTIAL', 'PENDING_ASSIGNMENT', 'LISTING', 'RENTAL', 2370201,
     null, null, null, null, null,
     false, false, false, false,
     false, false, false, false,
     false, false, false,
     false, false, false,
     100000, 'MONTHLY', 'XAF', null, null, null, null,
     null),
    (3, 1, 10, 20, 'RESIDENTIAL', 'APPROVED', 'LISTING', 'RENTAL', 2370201,
     null, null, null, null, null,
     false, false, false, false,
     false, false, false, false,
     false, false, false,
     false, false, false,
     100000, 'MONTHLY', 'XAF', null, null, null, null,
     null),
    (9, 2, 90, 90, 'RESIDENTIAL', 'APPROVED', 'LISTING', 'RENTAL', 2370201,
     null, null, null, null, null,
     false, false, false, false,
     false, false, false, false,
     false, false, false,
     false, false, false,
     100000, 'MONTHLY', 'XAF', null, null, null, null,
     null);

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES
    (1, 111),
    (1, 222);

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, status, city_id) VALUES
    (300, 1, 3, 20, 'DRAFT', 2370201);
