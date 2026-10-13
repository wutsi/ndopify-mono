INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (90, 2, 900);

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_types,
    furniture_type, rooms, parkings, min_area, max_area,
    water_access_type, water_tank, water_well, electricity_access_type, generator_set, solar_panel,
    road_access_type, secured_fenced, security_guard, video_surveillance,
    price, price_periodicity_type, currency_code, security_deposit, advance_rent, lease_term, notice_period,
    preferred_contact_period, comments, created_at, modified_at
) VALUES
    (1, 1, 10, null, 1, 1, 2, 2370201, 'APARTMENT,STUDIO',
     1, '2', '1', 50, 100,
     1, true, true, 1, true, true,
     1, true, true, true,
     150000, 4, 'XAF', 2, 3, 12, 1,
     1, 'Close to the market', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 2, 90, null, 1, 1, 2, 2370201, 'APARTMENT',
     null, null, null, null, null,
     null, false, false, null, false, false,
     null, false, false, false,
     100000, 4, 'XAF', null, null, null, null,
     null, null, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES
    (1, 111),
    (1, 222);
