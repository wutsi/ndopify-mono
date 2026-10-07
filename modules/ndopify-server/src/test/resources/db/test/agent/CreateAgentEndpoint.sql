INSERT INTO T_PARTY(id, email, first_name, last_name) VALUES
    (100, 'roger.milla@gmail.com', 'Roger', 'Milla'),
    (101, '007@gmail.com', 'James', 'Bond'),
    (102, 'omam.mbiyick@gmail.com', 'Omam', 'Mbiyick');

INSERT INTO T_AGENT(id, party_id) VALUES
    (102, 102);

INSERT INTO T_TENANT(id, active, name, domain_name, locales, country_code, number_format, currency_code, currency_symbol, monetary_format, date_format, time_format, date_time_format, logo_url, partner_central_url)
VALUES
    (1, true, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', '#,###,##0.00', 'XAF', 'FCFA', '#,###,##0 FCFA', 'yyyy-MM-dd', 'HH:mm', 'yyyy-MM-dd HH:mm', 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/logo.png', 'http://localhost:8081/partner-central');
