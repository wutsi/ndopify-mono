INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, tenant_id, party_id, type, issuing_country_code, number, number_suffix, status, created_at, modified_at) VALUES
    ('id-100', 1, 100, 1, 'CM', '1234567890', '7890', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('id-900', 2, 900, 1, 'CM', '0987654321', '4321', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION_IMAGE(id, tenant_id, identification_id, image_type, storage_type, path, mime_type, created_at, uploaded_at) VALUES
    ('img-100-front', 1, 'id-100', 1, 1, 'identifications/id-100/images/img-100-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:01'),
    ('img-100-back', 1, 'id-100', 2, 0, NULL, NULL, '2024-06-10 00:00:00', NULL),
    ('img-900-front', 2, 'id-900', 1, 1, 'identifications/id-900/images/img-900-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:01');
