INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Autre', 'other.localhost', 'fr-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'tenant1@example.com', 'One', 'Tenant'),
    (200, 2, 'tenant2@example.com', 'Two', 'Tenant');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (20, 2, 200);

-- 1: tenant 1's mandate   2: tenant 2's mandate
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, status, mandate_type, listing_type, city_id,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (1, 1, 10, 'APPROVED', 'TENANT', 'SEARCH', 2370201, '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 2, 20, 'APPROVED', 'TENANT', 'SEARCH', 2370201, '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 1: tenant 1's document   2: tenant 2's document
INSERT INTO T_MANDATE_DOCUMENT(id, tenant_id, mandate_id, type, title, status, version, created_at, modified_at) VALUES
    (1, 1, 1, 'LAND_TITLE', 'Titre foncier', 'UPLOADED', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 2, 2, 'LAND_TITLE', 'Titre foncier', 'UPLOADED', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_MANDATE_DOCUMENT_PAGE(id, tenant_id, document_id, page_index, path, storage_type, mime_type, created_at, modified_at) VALUES
    (1, 1, 1, 0, 'documents/1/page/0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 1, 1, 1, 'documents/1/page/1.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (3, 2, 2, 0, 'documents/2/page/0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (4, 2, 2, 1, 'documents/2/page/1.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
