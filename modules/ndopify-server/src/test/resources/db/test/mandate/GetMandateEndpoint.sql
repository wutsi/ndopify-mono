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
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (90, 2, 900);

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, property_category, status, mandate_type, listing_type, city_id, property_type,
    furniture_type, rooms, parkings, area,
    water_meter, dedicated_water_meter, water_tank, water_well,
    electricity_meter, dedicated_electricity_meter, generator_set, solar_panel,
    road_paved, track_accessible_by_car, track_inaccessible_by_car,
    secured_fenced, security_guard, video_surveillance,
    price, price_periodicity_type, currency_code, security_deposit, advance_rent, lease_term, notice_period, moving_period,
    preferred_contact_period, comments, created_at, modified_at
) VALUES
    (1, 1, 10, null, 'RESIDENTIAL', 'PENDING_ASSIGNMENT', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT',
     'UNFURNISHED', '2', '1', 75,
     true, true, true, true,
     true, false, true, true,
     false, true, false,
     true, true, true,
     150000, 'MONTHLY', 'XAF', 2, 3, 12, 1, 'WITHIN_3_MONTHS',
     'MORNING', 'Close to the market', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 2, 90, null, 'RESIDENTIAL', 'PENDING_ASSIGNMENT', 'TENANT', 'SEARCH', 2370201, 'APARTMENT',
     null, null, null, null,
     false, false, false, false,
     false, false, false, false,
     false, false, false,
     false, false, false,
     100000, 'MONTHLY', 'XAF', null, null, null, null, 'UNKNOWN',
     null, null, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES
    (1, 111),
    (1, 222);
