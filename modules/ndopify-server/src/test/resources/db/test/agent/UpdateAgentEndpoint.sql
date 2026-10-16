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

INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'roger.milla@gmail.com', 'Roger', 'Milla', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, '007@gmail.com', 'J', 'Bond', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 'samuel.eto@gmail.com', 'Samuel', 'Eto', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_AGENT(id, party_id) VALUES
    (100, 100),
    (101, 101),
    (102, 102);

INSERT INTO T_AGENT_NEIGHBORHOOD(agent_id, neighborhood_id) VALUES
    (102, 555),
    (102, 666);
