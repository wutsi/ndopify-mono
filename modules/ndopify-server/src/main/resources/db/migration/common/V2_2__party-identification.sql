CREATE TABLE T_IDENTIFICATION(
  id                      VARCHAR(36) NOT NULL,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  party_id                BIGINT NOT NULL,

  type                    VARCHAR(30) DEFAULT NULL,
  issuing_country_code    VARCHAR(2) NOT NULL,
  number                  VARCHAR(100),
  number_suffix           VARCHAR(4),
  issued_at               DATE,
  expires_at              DATE,
  status                  VARCHAR(30) DEFAULT NULL,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),


  CONSTRAINT FK_IDENTIFICATION_party FOREIGN KEY(party_id) REFERENCES T_PARTY(id),
  CONSTRAINT FK_IDENTIFICATION_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;
CREATE INDEX I_IDENTIFICATION_tenant ON T_IDENTIFICATION(tenant_id);
