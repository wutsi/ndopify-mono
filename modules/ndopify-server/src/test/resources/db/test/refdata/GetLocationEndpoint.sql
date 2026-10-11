INSERT INTO T_LOCATION (id, parent_fk, type, country, name, ascii_name, population, latitude, longitude)
    VALUES (237,     null,  1, 'CM', 'Cameroon', 'Cameroon', 28000000, 7.36, 12.35),
           (23702,   237,   2, 'CM', 'Ouest', 'Ouest', null, null, null),
           (2370201, 23702, 3, 'CM', 'Bafoussam', 'bafoussam', 347517, 5.47, 10.42)
;
