INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, tenant_id, party_id, type, issuing_country_code, number, number_suffix, status, created_at, modified_at) VALUES
    ('id-100', 1, 100, 1, 'CM', '1234567890', '7890', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('id-900', 2, 900, 1, 'CM', '0987654321', '4321', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION_IMAGE(id, identification_id, image_type, storage_type, path, mime_type, created_at) VALUES
    ('img-100-front', 'id-100', 1, 1, 'kyc/100/id-100/img-100-front.jpeg', 'image/jpeg', '2024-06-10 00:00:00'),
    ('img-100-back', 'id-100', 2, 1, 'kyc/100/id-100/img-100-back.jpeg', 'image/jpeg', '2024-06-10 00:00:00');
