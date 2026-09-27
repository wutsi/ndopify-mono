INSERT INTO T_LOCATION (id, parent_fk, type, country, name, ascii_name)
    VALUES (2233387, null,    1, 'CM', 'Cameroon', 'Cameroon'),
           (2233376, 2233387, 2, 'CM', 'Cantre', 'Centre'),
           (6297005, 2233376, 3, 'CM', 'Yaounde', 'ya'),
           (237021,  6297005, 4, 'CM', 'bastos', 'bastos')
;
