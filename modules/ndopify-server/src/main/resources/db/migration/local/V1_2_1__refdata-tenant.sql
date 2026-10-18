INSERT INTO T_TENANT(id, active, name, domain_name, locales, country_code, number_format,
    currency_code, currency_symbol, monetary_format, date_format, time_format, date_time_format,
    logo_url, icon_url, partner_central_url, public_portal_url)
VALUES
    (1, true, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', '#,###,##0.00',
    'XAF', 'FCFA', '#,###,##0 FCFA', 'dd MMM yyyy', 'HH:mm', 'dd MMM yyyy HH:mm',
    'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/logo.png', 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/icon.png',
    'http://localhost:8081', 'http://localhost:8082');
