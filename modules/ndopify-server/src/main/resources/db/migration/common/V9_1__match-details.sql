-- What the agent's page needs to explain a match, and why the customer rejected it. Rows matched before this script
-- get the distance and the missing amenities at their next re-score.
ALTER TABLE T_MATCH ADD COLUMN distance_km DOUBLE DEFAULT NULL AFTER furniture_score;
ALTER TABLE T_MATCH ADD COLUMN missing_amenities TEXT DEFAULT NULL AFTER distance_km;
ALTER TABLE T_MATCH ADD COLUMN rejection_reason_code VARCHAR(30) DEFAULT NULL AFTER listing_price;
