-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

-- Cities and neighborhoods referenced by the agents and mandates (T_AGENT, T_MANDATE and their neighborhoods)
INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde'),
    (2220957, NULL, 'CITY', 'CM', 'Douala', 'Douala'),
    (1514, NULL, 'CITY', 'CA', 'Montreal', 'Montreal'),
    (111, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos'),
    (222, 2370201, 'NEIGHBORHOOD', 'CM', 'Mvan', 'Mvan'),
    (333, 2370201, 'NEIGHBORHOOD', 'CM', 'Nlongkak', 'Nlongkak'),
    (444, 2370201, 'NEIGHBORHOOD', 'CM', 'Essos', 'Essos'),
    (555, 2370201, 'NEIGHBORHOOD', 'CM', 'Biyem-Assi', 'Biyem-Assi'),
    (666, 2370201, 'NEIGHBORHOOD', 'CM', 'Mendong', 'Mendong');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, kyc_status, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', 'VERIFIED', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 'kim.possible@gmail.com', 'Kim', 'Possible', 'REJECTED', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 1, 'ash.ketchum@gmail.com', 'Ash', 'Ketchum', 'PENDING', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', 'VERIFIED', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_AGENT (id, tenant_id, party_id, city_id, created_at, modified_at) VALUES
    (1, 1, 100, 2370201, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (2, 1, 101, 2370201, '2024-06-11 00:00:00', '2024-06-11 00:00:00'),
    (3, 1, 102, 1514, '2024-06-12 00:00:00', '2024-06-12 00:00:00'),
    (4, 2, 900, 2370201, '2024-06-13 00:00:00', '2024-06-13 00:00:00');

INSERT INTO T_AGENT_NEIGHBORHOOD (agent_id, neighborhood_id) VALUES
    (1, 111),
    (1, 222),
    (2, 333);

INSERT INTO T_PAYMENT_METHOD(id, party_id, number, type, status, created_at, modified_at) VALUES
    (uuid(), 101, '+237600000000', 'MOBILE_MONEY', 'PENDING_VERIFICATION', '2024-06-11 00:00:00', '2024-06-11 00:00:00');
