CREATE TABLE T_APPLICATION(
  id                      BIGINT NOT NULL,

  code                    VARCHAR(50) NOT NULL,
  supported_auth_types    TEXT,

  UNIQUE(code),
  PRIMARY KEY(id)
) ENGINE = InnoDB;


CREATE TABLE T_ROLE(
  id                      BIGINT  NOT NULL AUTO_INCREMENT,

  application_id          BIGINT NOT NULL REFERENCES T_APPLICATION(id),

  code                    VARCHAR(50) NOT NULL,
  active                  BOOLEAN NOT NULL DEFAULT TRUE,

  UNIQUE(application_id, code),
  PRIMARY KEY(id)
) ENGINE = InnoDB;


INSERT INTO T_APPLICATION(id, code, supported_auth_types)
VALUES
    (1, 'public-portal',   'PASSWORD,GOOGLE_ONE_TAP'),
    (2, 'admin-console',   'PASSWORD'),
    (3, 'partner-central', 'OTP')
;

INSERT INTO T_ROLE(application_id, code)
VALUES
    (2, 'admin'),
    (2, 'support')
;
