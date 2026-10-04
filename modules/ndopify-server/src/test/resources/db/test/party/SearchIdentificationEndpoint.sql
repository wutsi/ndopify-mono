INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, '007@gmail.com', 'J', 'Bond', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, party_id, type, issuing_country_code, number, number_suffix, status, created_at, modified_at) VALUES
    ('id-100-a', 100, 1, 'CM', '1234567890', '7890', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('id-100-b', 100, 2, 'CM', '9876543210', '3210', 3, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    ('id-101-a', 101, 1, 'US', '1112223334', '3334', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
