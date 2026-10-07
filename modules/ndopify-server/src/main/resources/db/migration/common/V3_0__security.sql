CREATE TABLE T_USER(
  id                     BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id              BIGINT,

  party_id               BIGINT REFERENCES T_PARTY(id),

  email                  VARCHAR(255) NOT NULL,
  deleted                BOOLEAN NOT NULL DEFAULT false,
  created_at             DATETIME DEFAULT NOW(),
  deleted_at             DATETIME,

  UNIQUE(email),
  UNIQUE(party_id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;
CREATE INDEX I_USER_tenant ON T_USER(tenant_id);

CREATE TABLE T_AUTH_FACTOR(
  id                     BIGINT NOT NULL AUTO_INCREMENT,

  user_id                BIGINT NOT NULL REFERENCES T_USER(id),

  auth_type              INT NOT NULL DEFAULT 0,
  data                   TEXT NOT NULL,
  salt                   VARCHAR(36),

  created_at             DATETIME DEFAULT NOW(),
  modified_at            DATETIME NOT NULL DEFAULT now() ON UPDATE now(),
  last_logged_in_at      DATETIME,
  expires_at             DATETIME,

  UNIQUE(user_id, auth_type),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE TABLE T_USER_APPLICATION(
  id                     BIGINT NOT NULL AUTO_INCREMENT,

  user_id                BIGINT NOT NULL REFERENCES T_USER(id),
  application_id         BIGINT NOT NULL REFERENCES T_APPLICATION(id),

  created_at             DATETIME DEFAULT NOW(),

  UNIQUE(user_id, application_id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;


CREATE TABLE T_USER_APPLICATION_ROLE(
  user_application_id       BIGINT NOT NULL REFERENCES T_USER_APPLICATION(id),
  role_id                   BIGINT NOT NULL REFERENCES T_ROLE(id),

  created_at             DATETIME DEFAULT NOW(),

  PRIMARY KEY(user_application_id, role_id)
) ENGINE = InnoDB;
