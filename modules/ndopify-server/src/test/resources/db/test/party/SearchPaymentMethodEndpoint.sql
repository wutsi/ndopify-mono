INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, '007@gmail.com', 'J', 'Bond', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, party_id, number, provider_name, holder_name, type, status, created_at, modified_at) VALUES
    ('pm-100-a', 100, '+237671234567', 'MTN', 'Ray Sponsible', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('pm-100-b', 100, '+237650000000', 'Orange', 'Ray Sponsible', 1, 2, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    ('pm-101-a', 101, '+237699999999', 'MTN', 'J Bond', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
