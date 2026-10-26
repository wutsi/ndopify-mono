-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde'),
    (2220957, NULL, 'CITY', 'CM', 'Douala', 'Douala'),
    (111, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos'),
    (222, 2370201, 'NEIGHBORHOOD', 'CM', 'Mvan', 'Mvan'),
    (333, 2220957, 'NEIGHBORHOOD', 'CM', 'Akwa', 'Akwa');

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

-- 1, 2, 3: mandates of agent 20   9: other tenant
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, property_category, status, mandate_type, listing_type, city_id,
    price, price_periodicity_type, currency_code
) VALUES
    (1, 1, 10, 20, 'RESIDENTIAL', 'APPROVED', 'LISTING', 'RENTAL', 2370201, 150000, 'MONTHLY', 'XAF'),
    (2, 1, 10, 20, 'RESIDENTIAL', 'APPROVED', 'LISTING', 'RENTAL', 2370201, 300000, 'MONTHLY', 'XAF'),
    (3, 1, 10, 20, 'RESIDENTIAL', 'APPROVED', 'LISTING', 'SALE', 2220957, 80000000, 'NONE', 'XAF'),
    (9, 2, 90, 90, 'RESIDENTIAL', 'APPROVED', 'LISTING', 'RENTAL', 2370201, 100000, 'MONTHLY', 'XAF');

-- 100: draft apartment in Bastos   101: draft house in Mvan   102: published, for sale in Douala   900: other tenant
INSERT INTO T_LISTING(
    id, tenant_id, version, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    landmark, access_directions, longitude, latitude,
    property_type, furniture_type, rooms, bathrooms, living_rooms, kitchens, parkings, area, levels, floor_level,
    water_meter, dedicated_water_meter, water_tank, water_well,
    electricity_meter, dedicated_electricity_meter, generator_set, solar_panel,
    road_paved, track_accessible_by_car, track_inaccessible_by_car,
    secured_fenced, security_guard, video_surveillance,
    price, currency_code, price_periodicity_type, security_deposit, advance_rent, lease_term, notice_period,
    available_from, agent_remarks, title, summary, description,
    created_at, modified_at, published_at
) VALUES
    (100, 1, 3, 1, 20, 'RESIDENTIAL', 'RENTAL', 'DRAFT', 2370201, 111,
     'Near the embassy', 'Second street on the left', 11.5, 3.9,
     'APARTMENT', 'FULLY_FURNISHED', 2, 1, 1, 1, 1, 75, 1, 3,
     true, true, false, false,
     true, true, true, false,
     true, true, false,
     true, true, false,
     150000, 'XAF', 'MONTHLY', 2, 3, 12, 1,
     '2026-11-01', 'Keys at the guard', 'Nice apartment', 'Two rooms in Bastos', 'A long description',
     '2026-10-01 10:00:00', '2026-10-02 10:00:00', '2026-10-03 10:00:00'),
    (101, 1, 0, 2, 20, 'RESIDENTIAL', 'RENTAL', 'DRAFT', 2370201, 222,
     null, null, null, null,
     'HOUSE', 'UNFURNISHED', 4, 2, 1, 1, 2, 200, 2, null,
     false, false, false, false,
     false, false, false, false,
     false, false, false,
     false, false, false,
     300000, 'XAF', 'MONTHLY', null, null, null, null,
     null, null, null, null, null,
     '2026-10-01 10:00:00', '2026-10-01 10:00:00', null),
    (102, 1, 0, 3, 20, 'RESIDENTIAL', 'SALE', 'PUBLISHED', 2220957, null,
     null, null, null, null,
     'APARTMENT', null, 3, 1, 1, 1, null, 120, null, null,
     false, false, false, false,
     false, false, false, false,
     false, false, false,
     false, false, false,
     80000000, 'XAF', 'NONE', null, null, null, null,
     '2027-03-01', null, null, null, null,
     '2026-10-01 10:00:00', '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (900, 2, 0, 9, 90, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 111,
     null, null, null, null,
     'APARTMENT', null, 2, 1, 1, 1, null, 70, null, null,
     false, false, false, false,
     false, false, false, false,
     false, false, false,
     false, false, false,
     100000, 'XAF', 'MONTHLY', null, null, null, null,
     null, null, null, null, null,
     '2026-10-01 10:00:00', '2026-10-01 10:00:00', '2026-10-01 10:00:00');

-- 100: its location already verified
INSERT INTO T_LISTING_SECTION(id, tenant_id, listing_id, section, verified_at) VALUES
    (1000, 1, 100, 'LOCATION', '2026-10-02 10:00:00');
