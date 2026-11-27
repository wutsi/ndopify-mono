-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Autre', 'other.localhost', 'fr-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

-- 100: customer   200: agent 20 (user 11, the caller)   300: agent 30 (user 12)
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'customer@gmail.com', 'Customer', 'One'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201);

-- 100: the caller's (two documents)   101: the caller's (one document)   200: another agent's   300: CLOSED
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, comments, created_at, modified_at
) VALUES
    (100, 1, 10, 20, 'ASSIGNED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Mine listing', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 10, 20, 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Mine search', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (200, 1, 10, 30, 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Not mine', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (300, 1, 10, 20, 'CLOSED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Closed', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 10/11: the two documents of mandate 100 (11 is newer)   12: mandate 101's document
INSERT INTO T_MANDATE_DOCUMENT(id, tenant_id, mandate_id, type, title, status, version, created_at, modified_at) VALUES
    (10, 1, 100, 'LAND_TITLE', 'Titre foncier', 'UPLOADED', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (11, 1, 100, 'PAY_SLIP', 'Bulletin mars', 'UPLOADED', 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (12, 1, 101, 'LAND_TITLE', 'Titre foncier', 'UPLOADED', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 10 has two S3 pages; 11 one LOCAL and one legacy (no storage type); 12 one S3 and one LOCAL
INSERT INTO T_MANDATE_DOCUMENT_PAGE(id, tenant_id, document_id, page_index, path, storage_type, mime_type, created_at, modified_at) VALUES
    (100, 1, 10, 0, 'mandate/100/documents/page-10-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 10, 1, 'mandate/100/documents/page-10-1.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 1, 11, 0, 'mandate/100/documents/page-11-0.png', 'LOCAL', 'image/png', '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (103, 1, 11, 1, 'mandate/100/documents/page-11-1.png', NULL, 'image/png', '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (104, 1, 12, 0, 'mandate/101/documents/page-12-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (105, 1, 12, 1, 'mandate/101/documents/page-12-1.png', 'LOCAL', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 13: an extracted land title   14: an expired identity (warning)   15: not extracted yet   16: a malformed payload
INSERT INTO T_MANDATE_DOCUMENT(id, tenant_id, mandate_id, type, title, status, problem_codes, data, data_version, issued_at, expires_at, version, created_at, modified_at) VALUES
    (13, 1, 100, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-1","owners":["A","B"],"locality":"Yaoundé","surface":"500","issueDate":"2020-01-01","valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.8,"locality":0.85}}',
     1, '2020-01-01', NULL, 0, '2024-06-12 00:00:00', '2024-06-12 00:00:00'),
    (14, 1, 100, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', 'DOCUMENT_EXPIRED',
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"John","lastName":"Doe","expiryDate":"2021-01-01","valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2021-01-01', 0, '2024-06-12 00:00:00', '2024-06-12 00:00:00'),
    (15, 1, 100, 'OTHER', 'Autre', 'UPLOADED', NULL, NULL, NULL, NULL, NULL, 0, '2024-06-12 00:00:00', '2024-06-12 00:00:00'),
    (16, 1, 100, 'OTHER', 'Autre', 'EXTRACTED', NULL, '[]', 1, NULL, NULL, 0, '2024-06-12 00:00:00', '2024-06-12 00:00:00');

-- 17: created from a PDF   18: a row written before the kind was recorded (source NULL)
INSERT INTO T_MANDATE_DOCUMENT(id, tenant_id, mandate_id, type, title, status, source, version, created_at, modified_at) VALUES
    (17, 1, 101, 'LAND_TITLE', 'Titre foncier', 'UPLOADED', 'PDF', 0, '2024-06-13 00:00:00', '2024-06-13 00:00:00'),
    (18, 1, 101, 'LAND_TITLE', 'Titre foncier', 'UPLOADED', NULL, 0, '2024-06-14 00:00:00', '2024-06-14 00:00:00');

-- 19: a grandfathered 11-page document (source NULL) on mandate 101
INSERT INTO T_MANDATE_DOCUMENT(id, tenant_id, mandate_id, type, title, status, source, version, created_at, modified_at) VALUES
    (19, 1, 101, 'LAND_TITLE', 'Onze pages', 'UPLOADED', NULL, 0, '2024-06-15 00:00:00', '2024-06-15 00:00:00');

INSERT INTO T_MANDATE_DOCUMENT_PAGE(id, tenant_id, document_id, page_index, path, storage_type, mime_type, created_at, modified_at) VALUES
    (2100, 1, 19, 0, 'mandate/101/documents/page-19-0.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00'),
    (2101, 1, 19, 1, 'mandate/101/documents/page-19-1.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00'),
    (2102, 1, 19, 2, 'mandate/101/documents/page-19-2.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00'),
    (2103, 1, 19, 3, 'mandate/101/documents/page-19-3.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00'),
    (2104, 1, 19, 4, 'mandate/101/documents/page-19-4.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00'),
    (2105, 1, 19, 5, 'mandate/101/documents/page-19-5.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00'),
    (2106, 1, 19, 6, 'mandate/101/documents/page-19-6.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00'),
    (2107, 1, 19, 7, 'mandate/101/documents/page-19-7.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00'),
    (2108, 1, 19, 8, 'mandate/101/documents/page-19-8.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00'),
    (2109, 1, 19, 9, 'mandate/101/documents/page-19-9.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00'),
    (2110, 1, 19, 10, 'mandate/101/documents/page-19-10.png', 'S3', 'image/png', '2024-06-15 00:00:00', '2024-06-15 00:00:00');
