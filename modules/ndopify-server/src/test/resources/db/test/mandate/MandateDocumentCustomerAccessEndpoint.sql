-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

-- 100: customer 10's party (user 11, the caller)   200: agent 20's party (user 12)
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'customer@gmail.com', 'Customer', 'One'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'customer@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00');

-- User 11 (the caller) is the mandate's customer's party, not its agent; user 12 is the agent's
INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false),
    (12, 1, 12, 200, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201);

-- 100: customer 10's mandate, assigned to agent 20
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, comments, created_at, modified_at
) VALUES
    (100, 1, 10, 20, 'ASSIGNED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Mine listing', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- 10: mandate 100's document, with two pages at known paths under the mandate's private folder
INSERT INTO T_MANDATE_DOCUMENT(id, tenant_id, mandate_id, type, title, status, version, created_at, modified_at) VALUES
    (10, 1, 100, 'LAND_TITLE', 'Titre foncier', 'UPLOADED', 0, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_MANDATE_DOCUMENT_PAGE(id, tenant_id, document_id, page_index, path, storage_type, mime_type, created_at, modified_at) VALUES
    (100, 1, 10, 0, 'mandate/100/documents/page-10-0.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 10, 1, 'mandate/100/documents/page-10-1.png', 'S3', 'image/png', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
