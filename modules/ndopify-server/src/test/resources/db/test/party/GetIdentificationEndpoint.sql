INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, tenant_id, party_id, type, issuing_country_code, number, number_suffix, status, created_at, modified_at) VALUES
    ('id-100', 1, 100, 1, 'CM', '1234567890', '7890', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('id-900', 2, 900, 1, 'CM', '0987654321', '4321', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
