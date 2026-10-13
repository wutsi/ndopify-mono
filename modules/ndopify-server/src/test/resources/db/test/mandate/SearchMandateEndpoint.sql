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

-- property_type: 1=ROOM, 3=APARTMENT, 4=VILLA | status: 1=PENDING_ASSIGNMENT, 2=ASSIGNED, 5=PUBLISHED | mandate_type: 1=TENANT, 2=OWNER | listing_type: 1=SALE, 2=RENTAL
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (1, 1, 10, null, 1, 1, 2, 2370201, 3, '2', 150000, 4, 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 1, 10, 20,   2, 2, 1, 2370201, 4, '4', 90000000, 1, 'XAF', '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (3, 1, 11, 20,   5, 1, 2, 2220957, 1, '1', 50000, 4, 'XAF', '2024-06-12 00:00:00', '2024-06-12 00:00:00'),
    (4, 2, 90, null, 1, 1, 2, 2370201, 3, '2', 100000, 4, 'XAF', '2024-06-13 00:00:00', '2024-06-13 00:00:00');

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES
    (1, 111),
    (1, 222),
    (2, 222),
    (3, 333);
