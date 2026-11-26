-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Autre', 'other.localhost', 'fr-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

-- 100: customer   200: agent 20 (user 11, the caller)
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'customer@gmail.com', 'Customer', 'One'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201);

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, comments, created_at, modified_at
) VALUES
    (100, 1, 10, 20, 'ASSIGNED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Mine listing', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 1001: an extracted title to send back   1002: its extraction is running   1003: an extracted pay slip
INSERT INTO T_MANDATE_DOCUMENT(id, tenant_id, mandate_id, type, title, status, problem_codes, data, data_version, issued_at, expires_at, version, created_at, modified_at) VALUES
    (1001, 1, 100, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', 'DOCUMENT_EXPIRED',
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-1","owners":["A"],"locality":"Yaoundé","valid":true}', 1,
     '2020-01-01', '2021-01-01', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1002, 1, 100, 'LAND_TITLE', 'Titre foncier', 'EXTRACTING', NULL, NULL, NULL, NULL, NULL, 0,
     '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1003, 1, 100, 'PAY_SLIP', 'Bulletin mars', 'EXTRACTED', NULL,
     '{"detectedType":"PAY_SLIP","employeeName":"A","payMonth":"2026-10","netSalary":350000,"valid":true}', 1,
     '2026-10-01', NULL, 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_MANDATE_DOCUMENT_PAGE(id, tenant_id, document_id, page_index, path, storage_type, mime_type, created_at, modified_at) VALUES
    (1001, 1, 1001, 0, 'mandate/100/documents/page-1001-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1002, 1, 1002, 0, 'mandate/100/documents/page-1002-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1003, 1, 1003, 0, 'mandate/100/documents/page-1003-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
