CREATE TABLE T_USER(
  id                     BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id              BIGINT,

  party_id               BIGINT,

  email                  VARCHAR(255) NOT NULL,
  deleted                BOOLEAN NOT NULL DEFAULT false,
  created_at             DATETIME DEFAULT NOW(),
  deleted_at             DATETIME,

  UNIQUE(email),
  UNIQUE(party_id),

  CONSTRAINT FK_USER_party FOREIGN KEY(party_id) REFERENCES T_PARTY(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;
CREATE INDEX I_USER_tenant ON T_USER(tenant_id);

CREATE TABLE T_AUTH_FACTOR(
  id                     BIGINT NOT NULL AUTO_INCREMENT,

  user_id                BIGINT NOT NULL,

  auth_type              INT NOT NULL DEFAULT 0,
  data                   TEXT NOT NULL,
  salt                   VARCHAR(36),

  created_at             DATETIME DEFAULT NOW(),
  modified_at            DATETIME NOT NULL DEFAULT now() ON UPDATE now(),
  last_logged_in_at      DATETIME,
  expires_at             DATETIME,

  UNIQUE(user_id, auth_type),

  CONSTRAINT FK_AUTH_FACTOR_user FOREIGN KEY(user_id) REFERENCES T_USER(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE TABLE T_USER_APPLICATION(
  id                     BIGINT NOT NULL AUTO_INCREMENT,

  user_id                BIGINT NOT NULL,
  application_id         BIGINT NOT NULL,

  created_at             DATETIME DEFAULT NOW(),

  UNIQUE(user_id, application_id),

  CONSTRAINT FK_USER_APPLICATION_user FOREIGN KEY(user_id) REFERENCES T_USER(id),
  CONSTRAINT FK_USER_APPLICATION_application FOREIGN KEY(application_id) REFERENCES T_APPLICATION(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;


CREATE TABLE T_USER_APPLICATION_ROLE(
  user_application_id       BIGINT NOT NULL,
  role_id                   BIGINT NOT NULL,

  created_at             DATETIME DEFAULT NOW(),

  CONSTRAINT FK_USER_APPLICATION_ROLE_user_application FOREIGN KEY(user_application_id) REFERENCES T_USER_APPLICATION(id),
  CONSTRAINT FK_USER_APPLICATION_ROLE_role FOREIGN KEY(role_id) REFERENCES T_ROLE(id),
  PRIMARY KEY(user_application_id, role_id)
) ENGINE = InnoDB;
