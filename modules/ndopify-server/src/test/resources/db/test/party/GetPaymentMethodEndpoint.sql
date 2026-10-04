INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, party_id, number, provider_name, holder_name, type, status, created_at, modified_at) VALUES
    ('pm-100', 100, '+237671234567', 'MTN', 'Ray Sponsible', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
