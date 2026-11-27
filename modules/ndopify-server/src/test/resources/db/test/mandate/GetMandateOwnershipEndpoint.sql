-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Autre', 'other.localhost', 'fr-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

-- 100: customer (user 13)   200: agent 20 (user 11, the caller)   300: agent 30 (user 12)   90: tenant 2's customer
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'customer@gmail.com', 'Customer', 'One'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (90, 2, 'other@gmail.com', 'Other', 'Customer');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'customer@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false),
    (13, 1, 13, 100, false);

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (90, 2, 90);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201);

-- 100/101/102: the caller's   103: another agent's   104: only offered to the caller   106/107: SEARCH   200: tenant 2
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, comments, created_at, modified_at
) VALUES
    (100, 1, 10, 20, 'ASSIGNED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Verified', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 10, 20, 'ASSIGNED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Lease only', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 1, 10, 20, 'ASSIGNED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Failed', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (103, 1, 10, 30, 'ASSIGNED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Not mine', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (104, 1, 10, null, 'PENDING_ASSIGNMENT', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Offered', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (106, 1, 10, 20, 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Search', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (107, 1, 10, 30, 'ASSIGNED', 'SEARCH', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Not mine search', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (200, 2, 90, null, 'PENDING_ASSIGNMENT', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', 'Other tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_ASSIGNMENT(id, tenant_id, mandate_id, agent_id, status, score, created_at, modified_at, expires_at) VALUES
    (1, 1, 104, 20, 'PENDING', 0.8, '2024-06-10 00:00:00', '2024-06-10 00:00:00', '2099-01-01 00:00:00');

-- 1000: the customer's valid ID   1001: the customer's land title   1002: a lease   1003: an expired ID
-- 1004: a title naming someone else   1005+: the documents of the mandates 103, 104, 106 and 107 (as 100)
INSERT INTO T_MANDATE_DOCUMENT(
    id, tenant_id, mandate_id, type, title, status, problem_codes, data, data_version, issued_at, expires_at,
    version, created_at, modified_at
) VALUES
    (1000, 1, 100, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', NULL,
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Customer","lastName":"One","expiryDate":"2099-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2099-01-01', 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1001, 1, 100, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-1","owners":["Customer One"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1002, 1, 101, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', NULL,
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Customer","lastName":"One","expiryDate":"2099-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2099-01-01', 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1003, 1, 101, 'LEASE_CONTRACT', 'Bail', 'EXTRACTED', NULL,
     '{"detectedType":"LEASE_CONTRACT","holderName":"Customer One","address":"Rue 1","valid":true,"fieldConfidences":{"holderName":0.9,"address":0.9}}',
     1, NULL, NULL, 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1004, 1, 102, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', 'DOCUMENT_EXPIRED',
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Customer","lastName":"One","expiryDate":"2020-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2020-01-01', 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1005, 1, 102, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-2","owners":["Pierre Durand"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1006, 1, 103, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', NULL,
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Customer","lastName":"One","expiryDate":"2099-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2099-01-01', 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1007, 1, 103, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-1","owners":["Customer One"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1008, 1, 104, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', NULL,
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Customer","lastName":"One","expiryDate":"2099-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2099-01-01', 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1009, 1, 104, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-1","owners":["Customer One"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1010, 1, 106, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', NULL,
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Customer","lastName":"One","expiryDate":"2099-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2099-01-01', 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1011, 1, 106, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-1","owners":["Customer One"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1012, 1, 107, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', NULL,
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Customer","lastName":"One","expiryDate":"2099-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2099-01-01', 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (1013, 1, 107, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-1","owners":["Customer One"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2024-06-11 00:00:00', '2024-06-11 00:00:00');
