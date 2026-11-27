-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

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

-- 100: the caller's LISTING   101: the caller's SEARCH   200: another agent's   300: CLOSED
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, comments, created_at, modified_at
) VALUES
    (100, 1, 10, 20, 'ASSIGNED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Mine listing', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 10, 20, 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Mine search', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (200, 1, 10, 30, 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Not mine', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (300, 1, 10, 20, 'CLOSED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Closed', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 1000: FAILED with extracted data (2 pages); 1001: one page; 1100: 101's; 3000: the closed mandate's
INSERT INTO T_MANDATE_DOCUMENT(id, tenant_id, mandate_id, type, title, status, problem_codes, data, data_version, issued_at, expires_at, version, created_at, modified_at) VALUES
    (1000, 1, 100, 'LAND_TITLE', 'Titre foncier', 'FAILED', 'BLURRY', '{"a":1}', 1, '2024-01-01', '2024-12-31', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1001, 1, 100, 'PAY_SLIP', 'Bulletin mars', 'UPLOADED', NULL, NULL, NULL, NULL, NULL, 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1100, 1, 101, 'LAND_TITLE', 'Titre foncier', 'UPLOADED', NULL, NULL, NULL, NULL, NULL, 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (3000, 1, 300, 'LAND_TITLE', 'Titre foncier', 'UPLOADED', NULL, NULL, NULL, NULL, NULL, 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 1000 has two pages, S3 then LOCAL (so a delete removes each with its own storage); the others one page each
INSERT INTO T_MANDATE_DOCUMENT_PAGE(id, tenant_id, document_id, page_index, path, storage_type, mime_type, created_at, modified_at) VALUES
    (2000, 1, 1000, 0, 'mandate/100/documents/page-1000-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2001, 1, 1000, 1, 'mandate/100/documents/page-1000-1.png', 'LOCAL', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2002, 1, 1001, 0, 'mandate/100/documents/page-1001-0.png', 'S3', 'image/png', '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (2003, 1, 1100, 0, 'mandate/101/documents/page-1100-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2004, 1, 3000, 0, 'mandate/300/documents/page-3000-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- #369: 1002 a PDF document (1 page)   1003 an image document (1 page)   1004 an image document at 10 pages
-- 1005 an image document over the cap (11 pages)   1006 a PDF document at 9 pages   1007 a grandfathered UNKNOWN one
INSERT INTO T_MANDATE_DOCUMENT(id, tenant_id, mandate_id, type, title, status, source, version, created_at, modified_at) VALUES
    (1002, 1, 100, 'LAND_TITLE', 'Titre foncier PDF', 'UPLOADED', 'PDF', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1003, 1, 100, 'LAND_TITLE', 'Titre foncier photos', 'UPLOADED', 'IMAGE', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1004, 1, 100, 'LAND_TITLE', 'Dix pages', 'UPLOADED', 'IMAGE', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1005, 1, 100, 'LAND_TITLE', 'Onze pages', 'UPLOADED', 'IMAGE', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1006, 1, 100, 'LAND_TITLE', 'Neuf pages PDF', 'UPLOADED', 'PDF', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1007, 1, 100, 'LAND_TITLE', 'Sans kind', 'UPLOADED', NULL, 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (1008, 1, 100, 'LAND_TITLE', 'Onze pages sans kind', 'UPLOADED', NULL, 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_MANDATE_DOCUMENT_PAGE(id, tenant_id, document_id, page_index, path, storage_type, mime_type, created_at, modified_at) VALUES
    (2005, 1, 1002, 0, 'mandate/100/documents/page-1002-0.jpg', 'S3', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2006, 1, 1003, 0, 'mandate/100/documents/page-1003-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2010, 1, 1004, 0, 'mandate/100/documents/page-1004-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2011, 1, 1004, 1, 'mandate/100/documents/page-1004-1.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2012, 1, 1004, 2, 'mandate/100/documents/page-1004-2.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2013, 1, 1004, 3, 'mandate/100/documents/page-1004-3.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2014, 1, 1004, 4, 'mandate/100/documents/page-1004-4.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2015, 1, 1004, 5, 'mandate/100/documents/page-1004-5.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2016, 1, 1004, 6, 'mandate/100/documents/page-1004-6.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2017, 1, 1004, 7, 'mandate/100/documents/page-1004-7.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2018, 1, 1004, 8, 'mandate/100/documents/page-1004-8.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2019, 1, 1004, 9, 'mandate/100/documents/page-1004-9.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2020, 1, 1005, 0, 'mandate/100/documents/page-1005-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2021, 1, 1005, 1, 'mandate/100/documents/page-1005-1.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2022, 1, 1005, 2, 'mandate/100/documents/page-1005-2.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2023, 1, 1005, 3, 'mandate/100/documents/page-1005-3.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2024, 1, 1005, 4, 'mandate/100/documents/page-1005-4.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2025, 1, 1005, 5, 'mandate/100/documents/page-1005-5.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2026, 1, 1005, 6, 'mandate/100/documents/page-1005-6.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2027, 1, 1005, 7, 'mandate/100/documents/page-1005-7.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2028, 1, 1005, 8, 'mandate/100/documents/page-1005-8.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2029, 1, 1005, 9, 'mandate/100/documents/page-1005-9.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2030, 1, 1005, 10, 'mandate/100/documents/page-1005-10.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2040, 1, 1006, 0, 'mandate/100/documents/page-1006-0.jpg', 'S3', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2041, 1, 1006, 1, 'mandate/100/documents/page-1006-1.jpg', 'S3', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2042, 1, 1006, 2, 'mandate/100/documents/page-1006-2.jpg', 'S3', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2043, 1, 1006, 3, 'mandate/100/documents/page-1006-3.jpg', 'S3', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2044, 1, 1006, 4, 'mandate/100/documents/page-1006-4.jpg', 'S3', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2045, 1, 1006, 5, 'mandate/100/documents/page-1006-5.jpg', 'S3', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2046, 1, 1006, 6, 'mandate/100/documents/page-1006-6.jpg', 'S3', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2047, 1, 1006, 7, 'mandate/100/documents/page-1006-7.jpg', 'S3', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2048, 1, 1006, 8, 'mandate/100/documents/page-1006-8.jpg', 'S3', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2050, 1, 1007, 0, 'mandate/100/documents/page-1007-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2060, 1, 1008, 0, 'mandate/100/documents/page-1008-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2061, 1, 1008, 1, 'mandate/100/documents/page-1008-1.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2062, 1, 1008, 2, 'mandate/100/documents/page-1008-2.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2063, 1, 1008, 3, 'mandate/100/documents/page-1008-3.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2064, 1, 1008, 4, 'mandate/100/documents/page-1008-4.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2065, 1, 1008, 5, 'mandate/100/documents/page-1008-5.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2066, 1, 1008, 6, 'mandate/100/documents/page-1008-6.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2067, 1, 1008, 7, 'mandate/100/documents/page-1008-7.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2068, 1, 1008, 8, 'mandate/100/documents/page-1008-8.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2069, 1, 1008, 9, 'mandate/100/documents/page-1008-9.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2070, 1, 1008, 10, 'mandate/100/documents/page-1008-10.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
