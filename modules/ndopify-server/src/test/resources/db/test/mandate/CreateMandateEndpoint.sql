-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

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

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, '007@gmail.com', 'James', 'Bond'),
    (101, 1, 'omam.mbiyick@gmail.com', 'Omam', 'Mbiyick');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (101, 1, 101);
