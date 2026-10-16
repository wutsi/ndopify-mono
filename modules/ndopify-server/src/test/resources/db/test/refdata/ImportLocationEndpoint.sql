INSERT INTO T_LOCATION (id, parent_fk, type, country, name, ascii_name)
    VALUES (2233387, null,    'COUNTRY', 'CM', 'Cameroon', 'Cameroon'),
           (2233376, 2233387, 'STATE', 'CM', 'Cantre', 'Centre'),
           (6297005, 2233376, 'CITY', 'CM', 'Yaounde', 'ya'),
           (237021,  6297005, 'NEIGHBORHOOD', 'CM', 'bastos', 'bastos')
;
