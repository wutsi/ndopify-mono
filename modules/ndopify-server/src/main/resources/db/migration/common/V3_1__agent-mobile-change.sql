CREATE TABLE T_MOBILE_CHANGE(
  id                     BIGINT NOT NULL AUTO_INCREMENT,

  tenant_id              BIGINT NOT NULL,
  agent_id               BIGINT NOT NULL REFERENCES T_AGENT(id),
  verify_by_user_id      BIGINT,

  old_mobile_number      VARCHAR(20),
  new_mobile_number      VARCHAR(20) NOT NULL,
  new_gateway            INT NOT NULL DEFAULT 0,
  holder_name            VARCHAR(200),
  status                 INT  NOT NULL DEFAULT 0,
  error_code             VARCHAR(100),
  retries                INT,
  failure_reason         TEXT,

  created_at             DATETIME DEFAULT NOW(),
  verified_at            DATETIME,

  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_MOBILE_CHANGE_tenant ON T_MOBILE_CHANGE(tenant_id);
CREATE INDEX I_MOBILE_CHANGE_verify_by_user ON T_MOBILE_CHANGE(verify_by_user_id);
CREATE INDEX I_MOBILE_CHANGE_status ON T_MOBILE_CHANGE(status);
