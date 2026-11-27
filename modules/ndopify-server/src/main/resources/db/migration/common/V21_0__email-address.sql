-- The SHA-256 of the normalized address only, so this table holds no plaintext address. The six verdicts are kept
-- beside the score because a policy change must cost no new call to SES. The table is global (no tenant_id), like
-- T_USER; expires_at is indexed for a future prune job.
CREATE TABLE T_EMAIL_ADDRESS(
  id                    BIGINT NOT NULL AUTO_INCREMENT,
  email_hash            CHAR(64) NOT NULL,
  score                 DOUBLE DEFAULT NULL,
  reason                VARCHAR(30) NOT NULL,
  has_valid_syntax      VARCHAR(10) NOT NULL,
  has_valid_dns_records VARCHAR(10) NOT NULL,
  mailbox_exists        VARCHAR(10) NOT NULL,
  is_role_address       VARCHAR(10) NOT NULL,
  is_disposable         VARCHAR(10) NOT NULL,
  is_random_input       VARCHAR(10) NOT NULL,
  checked_at            DATETIME NOT NULL,
  expires_at            DATETIME NOT NULL,
  PRIMARY KEY(id),
  CONSTRAINT UK_EMAIL_ADDRESS_hash UNIQUE(email_hash)
) ENGINE = InnoDB;

CREATE INDEX I_EMAIL_ADDRESS_expires_at ON T_EMAIL_ADDRESS(expires_at);
