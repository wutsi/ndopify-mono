INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, tenant_id, party_id, number, provider_name, holder_name, type, status, created_at, modified_at) VALUES
    ('pm-100', 1, 100, '+237671234567', 'MTN', 'Ray Sponsible', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('pm-900', 2, 900, '+237679000000', 'MTN', 'Other Tenant', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
