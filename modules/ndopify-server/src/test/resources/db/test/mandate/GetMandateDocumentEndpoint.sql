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
