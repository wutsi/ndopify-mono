INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, active, public_portal_url) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', true, 'https://www.ndopify.com');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaoundé', 'Yaounde'),
    (111, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201);

INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, city_id) VALUES
    (1, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (2, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (3, 1, 10, 20, 'APPROVED', 'LISTING', 2370201);

-- 100: the owner is verified   101: no document, the owner's verdict is pending
-- 102: an expired ID and a land title naming somebody else, the owner failed
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, status, city_id, neighborhood_id, retries, modified_at) VALUES
    (100, 1, 1, 20, 'VERIFIED', 2370201, 111, 0, '2026-01-01'),
    (101, 1, 2, 20, 'VERIFIED', 2370201, 111, 0, '2026-01-01'),
    (102, 1, 3, 20, 'VERIFIED', 2370201, 111, 0, '2026-01-01');

INSERT INTO T_MANDATE_DOCUMENT(
    id, tenant_id, mandate_id, type, title, status, problem_codes, data, data_version, issued_at, expires_at,
    version, created_at, modified_at
) VALUES
    (1000, 1, 1, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', NULL,
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Ray","lastName":"Sponsible","expiryDate":"2099-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2099-01-01', 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1001, 1, 1, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-9","owners":["Ray Sponsible"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1002, 1, 3, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', 'DOCUMENT_EXPIRED',
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Ray","lastName":"Sponsible","expiryDate":"2020-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2020-01-01', 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1003, 1, 3, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-8","owners":["Pierre Durand"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00');
