INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 'kim.possible@gmail.com', 'Kim', 'Possible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 1, 'ash.ketchum@gmail.com', 'Ash', 'Ketchum', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_CUSTOMER (id, tenant_id, party_id, whatsapp_number, created_at, modified_at) VALUES
    (1, 1, 100, '+23761111111', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 1, 101, '+23761111112', '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (3, 1, 102, null, '2024-06-12 00:00:00', '2024-06-12 00:00:00'),
    (4, 2, 900, '+23762222222', '2024-06-13 00:00:00', '2024-06-13 00:00:00');
