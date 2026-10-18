CREATE TABLE T_KYC_CASE(
  id                      VARCHAR(36) NOT NULL,
  tenant_id               BIGINT NOT NULL DEFAULT 1,
  version                 BIGINT NOT NULL DEFAULT 0,

  party_id                BIGINT NOT NULL,

  identification_type     VARCHAR(30) DEFAULT NULL,
  score                   INT,
  status                  VARCHAR(30) DEFAULT NULL,
  retries                 INT DEFAULT NULL,
  error_code              VARCHAR(100) DEFAULT NULL,
  error_message           TEXT DEFAULT NULL,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),


  CONSTRAINT FK_KYC_CASE_party FOREIGN KEY(party_id) REFERENCES T_PARTY(id),
  CONSTRAINT FK_KYC_CASE_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;
CREATE INDEX I_KYC_CASE_tenant ON T_KYC_CASE(tenant_id);

CREATE TABLE T_KYC_IMAGE(
  id                      VARCHAR(36) NOT NULL,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  case_id                 VARCHAR(36) NOT NULL,

  identification_type     VARCHAR(30) DEFAULT NULL,
  image_type              VARCHAR(30) DEFAULT NULL,
  storage_type            VARCHAR(30) DEFAULT NULL,
  path                    TEXT,
  mime_type               VARCHAR(100),

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  UNIQUE(case_id, identification_type, image_type),

  CONSTRAINT FK_KYC_IMAGE_case FOREIGN KEY(case_id) REFERENCES T_KYC_CASE(id),
  CONSTRAINT FK_KYC_IMAGE_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;
CREATE INDEX I_KYC_IMAGE_tenant ON T_KYC_IMAGE(tenant_id);

CREATE TABLE T_KYC_VERIFICATION(
  id                      VARCHAR(36) NOT NULL,

  case_id                 VARCHAR(36) NOT NULL,
  payment_method_id       VARCHAR(36),

  type                    VARCHAR(30) DEFAULT NULL,
  status                  VARCHAR(30) DEFAULT NULL,
  score                   INT,
  error_code              VARCHAR(100),
  error_message           TEXT,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),


  CONSTRAINT FK_KYC_VERIFICATION_case FOREIGN KEY(case_id) REFERENCES T_KYC_CASE(id),
  CONSTRAINT FK_KYC_VERIFICATION_payment_method FOREIGN KEY(payment_method_id) REFERENCES T_PAYMENT_METHOD(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;
