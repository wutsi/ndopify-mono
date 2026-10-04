INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 'kim.possible@gmail.com', 'Kim', 'Possible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 'ash.ketchum@gmail.com', 'Ash', 'Ketchum', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_AGENT (id, party_id, city_id, created_at, modified_at) VALUES
    (1, 100, 2370201, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 101, 2370201, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (3, 102, 1514, '2024-06-12 00:00:00', '2024-06-12 00:00:00');

INSERT INTO T_AGENT_NEIGHBORHOOD (agent_id, neighborhood_id) VALUES
    (1, 111),
    (1, 222),
    (2, 333);

INSERT INTO T_PAYMENT_METHOD(id, party_id, number, type, status, created_at, modified_at) VALUES
    (uuid(), 101, '+237600000000', 1, 1, '2024-06-11 00:00:00', '2024-06-11 00:00:00');
