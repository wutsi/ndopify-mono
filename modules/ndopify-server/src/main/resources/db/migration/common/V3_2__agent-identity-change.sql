CREATE TABLE T_IDENTITY_CHANGE(
  id                     BIGINT NOT NULL AUTO_INCREMENT,

  tenant_id              BIGINT NOT NULL,
  agent_id               BIGINT NOT NULL REFERENCES T_AGENT(id),
  verify_by_user_id      BIGINT,

  old_first_name         VARCHAR(255) NOT NULL DEFAULT '',
  old_last_name          VARCHAR(255) NOT NULL DEFAULT '',
  new_first_name         VARCHAR(255) NOT NULL DEFAULT '',
  new_last_name          VARCHAR(255) NOT NULL DEFAULT '',
  identity_type          INT NOT NULL DEFAULT 0,
  document_page1url      VARCHAR(255) NOT NULL DEFAULT '',
  document_page2url      VARCHAR(255),

  holder_name            VARCHAR(255),
  number                 VARCHAR(255),
  expiry_date            DATETIME,

  status                 INT NOT NULL DEFAULT 0,
  error_code             VARCHAR(100),
  retries                INT,
  failure_reason         TEXT,

  created_at             DATETIME DEFAULT NOW(),
  verified_at            DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_IDENTITY_CHANGE_tenant ON T_IDENTITY_CHANGE(tenant_id);
CREATE INDEX I_IDENTITY_CHANGE_status ON T_IDENTITY_CHANGE(status);
CREATE INDEX I_IDENTITY_CHANGE_verify_by_user ON T_IDENTITY_CHANGE(verify_by_user_id);
