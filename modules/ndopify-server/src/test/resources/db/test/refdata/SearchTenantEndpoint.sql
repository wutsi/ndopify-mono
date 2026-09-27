INSERT INTO T_TENANT(id, active, name, domain_name, locales, number_format, currency_code, currency_symbol, monetary_format, date_format, time_format, date_time_format, logo_url, icon_url, country_code)
    VALUES
        (1, true,  'test', 'localhost', 'en_CA,fr_CA', '#,###,###',    'CAD', 'CA$',  'CA$ #,###,###.#0', 'yyyy-MM-dd', 'hh:mm a', 'yyyy-MM-dd hh:mm a', 'https://prod-wutsi.s3.amazonaws.com/static/wutsi-blog-web/assets/wutsi/img/logo/name-104x50.png', 'https://prod-wutsi.s3.amazonaws.com/static/wutsi-blog-web/assets/wutsi/img/logo/logo_512x512.png', 'CA'),
        (2, true,  'A',    'test.ca',   'en_CA',       '#,###,###.#0', 'CAD', 'CA$',  'CA$ #,###,###.#0', 'yyyy-MM-dd', 'HH:mm',   'yyyy-MM-dd HH:mm', null, null, 'CA'),
        (3, false, 'B',    'test.com',  'en_US',       '#,###,###.#0', 'USD', '$',    '$ #,###,###.#0',   'yyyy-MM-dd', 'hh:mm a', 'yyyy-MM-dd hh:mm a', null, null, 'US'),
        (4, false, 'C',    'test.fr',   'fr_FR',       '#,###,###.#0', 'EUR', '€',    '€ #,###,###.#0',   'yyyy-MM-dd', 'HH:mm',   'yyyy-MM-dd HH:mm', null, null, 'FR'),
        (5, true,  'D',    'test.cm',   'fr_CM',       '#,###,###',    'XAF', 'FCFA', '#,###,### FCFA',   'yyyy-MM-dd', 'HH:mm',   'yyyy-MM-dd HH:mm', null, null, 'CM')
;
