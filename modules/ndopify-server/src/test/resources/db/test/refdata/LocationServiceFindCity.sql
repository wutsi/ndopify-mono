INSERT INTO T_LOCATION (id, parent_fk, type, country, name, ascii_name)
    VALUES (237,     null,    'COUNTRY', 'CM', 'Cameroon',   'Cameroon'),
           (151,     null,    'COUNTRY', 'CA', 'Canada',     'Canada'),
           (2370101, 237,     'CITY', 'CM', 'Yaounde',    'Yaounde'),
           (2370201, 237,     'CITY', 'CM', 'Douala',     'Douala'),
           (2370301, 237,     'CITY', 'CM', 'Sainte-Anne', 'Sainte-Anne'),
           (237010101, 2370101, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos'),
           (151401, 151,     'CITY', 'CA', 'Montreal',   'Montreal');
