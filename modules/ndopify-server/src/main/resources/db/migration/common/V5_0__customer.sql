CREATE TABLE T_CUSTOMER(
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  party_id                BIGINT NOT NULL REFERENCES T_PARTY(id),

  whatsapp_number         VARCHAR(20),
  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  UNIQUE (party_id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_CUSTOMER_tenant ON T_CUSTOMER(tenant_id);
