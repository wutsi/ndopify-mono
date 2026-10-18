CREATE TABLE T_PARTY(
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  first_name              VARCHAR(100) NOT NULL,
  last_name               VARCHAR(100) NOT NULL,
  email                   VARCHAR(255) NOT NULL,
  photo_url               TEXT,
  kyc_status              VARCHAR(30) DEFAULT NULL,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  UNIQUE(tenant_id, email),

  CONSTRAINT FK_PARTY_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;
CREATE INDEX I_PARTY_tenant ON T_PARTY(tenant_id);
