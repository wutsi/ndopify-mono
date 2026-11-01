-- V9_0 was edited after some databases had applied it: they lack the columns added since (listing price, proposal and
-- rejection). Each change below only runs when missing, since a database that applied the current V9_0 already has it
-- (MySQL has no ADD COLUMN IF NOT EXISTS).

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE T_MATCH ADD COLUMN listing_price BIGINT NOT NULL DEFAULT -1 AFTER furniture_score',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'T_MATCH' AND COLUMN_NAME = 'listing_price');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE T_MATCH ADD COLUMN rejection_reason TEXT DEFAULT NULL AFTER listing_price',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'T_MATCH' AND COLUMN_NAME = 'rejection_reason');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE T_MATCH ADD COLUMN notified_at DATETIME DEFAULT NULL',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'T_MATCH' AND COLUMN_NAME = 'notified_at');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE T_MATCH ADD COLUMN proposed_at DATETIME DEFAULT NULL AFTER notified_at',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'T_MATCH' AND COLUMN_NAME = 'proposed_at');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE T_MATCH ADD COLUMN answered_at DATETIME DEFAULT NULL AFTER proposed_at',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'T_MATCH' AND COLUMN_NAME = 'answered_at');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'CREATE INDEX IDX_MATCH_notify ON T_MATCH(status, notified_at)',
    'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'T_MATCH' AND INDEX_NAME = 'IDX_MATCH_notify');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
