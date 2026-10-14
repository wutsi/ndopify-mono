INSERT INTO T_TENANT(id, active, name, domain_name, locales, country_code, number_format, currency_code, currency_symbol, monetary_format, date_format, time_format, date_time_format, logo_url, partner_central_url)
VALUES
    (1, true, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', '#,###,##0.00', 'XAF', 'FCFA', '#,###,##0 FCFA', 'yyyy-MM-dd', 'HH:mm', 'yyyy-MM-dd HH:mm', 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/logo.png', 'http://localhost:8081/partner-central');

-- Cities and neighborhoods referenced by the agents and mandates (T_AGENT, T_MANDATE and their neighborhoods)
INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 3, 'CM', 'Yaounde', 'Yaounde'),
    (2220957, NULL, 3, 'CM', 'Douala', 'Douala'),
    (1514, NULL, 3, 'CA', 'Montreal', 'Montreal'),
    (111, 2370201, 4, 'CM', 'Bastos', 'Bastos'),
    (222, 2370201, 4, 'CM', 'Mvan', 'Mvan'),
    (333, 2370201, 4, 'CM', 'Nlongkak', 'Nlongkak'),
    (444, 2370201, 4, 'CM', 'Essos', 'Essos'),
    (555, 2370201, 4, 'CM', 'Biyem-Assi', 'Biyem-Assi'),
    (666, 2370201, 4, 'CM', 'Mendong', 'Mendong');

INSERT INTO T_PARTY(id, email, first_name, last_name) VALUES
    (100, 'roger.milla@gmail.com', 'Roger', 'Milla'),
    (101, '007@gmail.com', 'James', 'Bond'),
    (102, 'omam.mbiyick@gmail.com', 'Omam', 'Mbiyick');

INSERT INTO T_AGENT(id, party_id) VALUES
    (102, 102);


-- A user who is already a member of another tenant
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_USER(id, email) VALUES
    (200, 'samuel.etoo@gmail.com');

INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id) VALUES
    (200, 2, 200);
