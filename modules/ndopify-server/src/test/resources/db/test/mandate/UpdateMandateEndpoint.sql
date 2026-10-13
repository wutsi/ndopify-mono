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
    (1, 1, 10, null, 1, 1, 1, 2, 2370201, 3,
     1, '2', '1', 75,
     true, true, true, true,
     true, false, true, true,
     false, true, false,
     true, true, true,
     150000, 4, 'XAF', 2, 3, 12, 1, 2,
     1, 'Close to the market', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 2, 90, null, 1, 1, 1, 2, 2370201, 3,
     null, null, null, null,
     false, false, false, false,
     false, false, false, false,
     false, false, false,
     false, false, false,
     100000, 4, 'XAF', null, null, null, null, 0,
     null, null, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES
    (1, 111),
    (1, 222);
