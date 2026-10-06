INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'tenant1@example.com', 'Tenant', 'One', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (200, 2, 'tenant2@example.com', 'Tenant', 'Two', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, tenant_id, party_id, number, type, status, created_at, modified_at) VALUES
    ('pm-100', 1, 100, '+237671111111', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('pm-200', 2, 200, '+237672222222', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, tenant_id, party_id, type, issuing_country_code, status, created_at, modified_at) VALUES
    ('id-100', 1, 100, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('id-200', 2, 200, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION_IMAGE(id, tenant_id, identification_id, image_type, storage_type, path, mime_type, created_at, uploaded_at) VALUES
    ('img-100', 1, 'id-100', 1, 1, 'identifications/id-100/images/img-100.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:01'),
    ('img-200', 2, 'id-200', 1, 1, 'identifications/id-200/images/img-200.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:01');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, identification_id, payment_method_id, status, created_at, modified_at) VALUES
    ('case-100', 1, 100, 'id-100', 'pm-100', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('case-200', 2, 200, 'id-200', 'pm-200', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
