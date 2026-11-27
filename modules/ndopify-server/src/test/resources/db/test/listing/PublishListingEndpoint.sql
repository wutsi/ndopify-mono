-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (90, 2, 900);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (90, 2, 900, 2370201);

INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, city_id) VALUES
    (1, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (2, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (3, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (4, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (5, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (6, 1, 10, 20, 'APPROVED', 'LISTING', 2370201),
    (9, 2, 90, 90, 'APPROVED', 'LISTING', 2370201);

-- 100: verified, with retries left by its verification   101: not verified yet   102: already published
-- 103: verified, owner without documents   104: verified, owner failed   105: verified, owner in flight
-- 900: other tenant, verified
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, status, city_id, retries, modified_at) VALUES
    (100, 1, 1, 20, 'VERIFIED', 2370201, 2, '2026-01-01'),
    (101, 1, 2, 20, 'UNDER_VERIFICATION', 2370201, NULL, '2026-01-01'),
    (102, 1, 3, 20, 'PUBLISHED', 2370201, NULL, '2026-01-01'),
    (103, 1, 4, 20, 'VERIFIED', 2370201, NULL, '2026-01-01'),
    (104, 1, 5, 20, 'VERIFIED', 2370201, NULL, '2026-01-01'),
    (105, 1, 6, 20, 'VERIFIED', 2370201, NULL, '2026-01-01'),
    (900, 2, 9, 90, 'VERIFIED', 2370201, NULL, '2026-01-01');

-- Listing 100's mandate: a valid ID and a land title naming its customer, so the owner is verified
-- Listing 104's mandate: an expired ID and a land title naming somebody else, so the owner failed
-- Listing 105's mandate: an uploaded ID and a valid land title, so the owner's verdict is in flight
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
    (1002, 1, 5, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', 'DOCUMENT_EXPIRED',
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Ray","lastName":"Sponsible","expiryDate":"2020-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2020-01-01', 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1003, 1, 5, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-8","owners":["Pierre Durand"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1004, 1, 6, 'NATIONAL_ID', 'Carte d''identité', 'UPLOADED', NULL,
     NULL, NULL, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1005, 1, 6, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-9","owners":["Ray Sponsible"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00');
