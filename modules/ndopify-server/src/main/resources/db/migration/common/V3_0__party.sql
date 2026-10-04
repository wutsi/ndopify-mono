CREATE TABLE T_PARTY(
  id                      BIGINT NOT NULL AUTO_INCREMENT,

  first_name              VARCHAR(100) NOT NULL,
  last_name               VARCHAR(100) NOT NULL,
  email                   VARCHAR(255) NOT NULL,
  photo_url               TEXT,
  kyc_status              INT NOT NULL DEFAULT 0,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  UNIQUE(email),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE TABLE T_PAYMENT_METHOD(
  id                      VARCHAR(36) NOT NULL,

  party_id                BIGINT NOT NULL REFERENCES T_PARTY(id),

  number                  VARCHAR(20) NOT NULL,
  provider_name           VARCHAR(100),
  holder_name             VARCHAR(100),
  expires_at              DATE,
  type                    INT NOT NULL,
  status                  INT NOT NULL DEFAULT 0,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  UNIQUE(party_id, number, type),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE TABLE T_IDENTIFICATION(
  id                      VARCHAR(36) NOT NULL,

  party_id                BIGINT NOT NULL REFERENCES T_PARTY(id),

  type                    INT NOT NULL DEFAULT 0,
  issuing_country_code    VARCHAR(2) NOT NULL,
  number                  VARCHAR(100),
  number_suffix           VARCHAR(4),
  issued_at               DATE,
  expires_at              DATE,
  status                  INT NOT NULL DEFAULT 0,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE TABLE T_IDENTIFICATION_IMAGE(
  id                      VARCHAR(36) NOT NULL,

  identification_id       VARCHAR(36) NOT NULL REFERENCES T_IDENTIFICATION(id),

  image_type              INT NOT NULL DEFAULT 0,
  storage_type            INT NOT NULL DEFAULT 0,
  path                    TEXT,
  mime_type               VARCHAR(100),
  uploaded                BOOLEAN NOT NULL DEFAULT FALSE,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),
  uploaded_at             DATETIME,

  PRIMARY KEY(id)
) ENGINE = InnoDB;
