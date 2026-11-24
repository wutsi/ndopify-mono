-- Land is a residential property type (it used to be stored as commercial): realign the saved answers.
UPDATE T_MANDATE
SET property_category = 'RESIDENTIAL'
WHERE property_type = 'LAND' AND property_category = 'COMMERCIAL';

UPDATE T_LISTING
SET property_category = 'RESIDENTIAL'
WHERE property_type = 'LAND' AND property_category = 'COMMERCIAL';
