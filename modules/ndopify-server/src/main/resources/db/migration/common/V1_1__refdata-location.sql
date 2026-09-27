CREATE TABLE T_LOCATION(
  id          BIGINT NOT NULL,

  parent_fk   BIGINT,

  name        VARCHAR(200) NOT NULL,
  ascii_name  VARCHAR(200) NOT NULL,
  type        INT NOT NULL DEFAULT 0,
  country     VARCHAR(2) NOT NULL,
  population  BIGINT,
  latitude    DOUBLE,
  longitude   DOUBLE,

  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX T_LOCATION_parent ON T_LOCATION(parent_fk);
CREATE INDEX I_LOCATION_country ON T_LOCATION(country);
CREATE INDEX I_LOCATION_type ON T_LOCATION(type);
