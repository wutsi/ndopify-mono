INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 'kim.possible@gmail.com', 'Kim', 'Possible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 1, 'ash.ketchum@gmail.com', 'Ash', 'Ketchum', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_AGENT (id, tenant_id, party_id, city_id, created_at, modified_at) VALUES
    (1, 1, 100, 2370201, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 1, 101, 2370201, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (3, 1, 102, 1514, '2024-06-12 00:00:00', '2024-06-12 00:00:00'),
    (4, 2, 900, 2370201, '2024-06-13 00:00:00', '2024-06-13 00:00:00');

INSERT INTO T_AGENT_NEIGHBORHOOD (agent_id, neighborhood_id) VALUES
    (1, 111),
    (1, 222),
    (2, 333);

INSERT INTO T_PAYMENT_METHOD(id, party_id, number, type, status, created_at, modified_at) VALUES
    (uuid(), 101, '+237600000000', 1, 1, '2024-06-11 00:00:00', '2024-06-11 00:00:00');
