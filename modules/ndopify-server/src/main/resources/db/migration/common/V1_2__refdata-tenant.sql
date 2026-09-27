CREATE TABLE T_TENANT(
  id                      BIGINT NOT NULL,

  name                    VARCHAR(100) NOT NULL,
  domain_name             VARCHAR(100) NOT NULL,
  locales                 VARCHAR(255) NOT NULL,
  country_code            VARCHAR(2) NOT NULL,
  currency_code           VARCHAR(3) NOT NULL  DEFAULT 'XAF',
  number_format           VARCHAR(20) NOT NULL DEFAULT '#,###,###.00',
  currency_symbol         VARCHAR(20) NOT NULL DEFAULT '$',
  monetary_format         VARCHAR(20) NOT NULL DEFAULT '#,###,##0 FCFA',
  date_format             VARCHAR(20) NOT NULL DEFAULT 'yyyy-MM-dd',
  time_format             VARCHAR(20) NOT NULL DEFAULT 'HH:mm',
  date_time_format        VARCHAR(20) NOT NULL DEFAULT 'yyyy-MM-dd HH:mm',
  active                  BOOLEAN NOT NULL DEFAULT true,
  logo_url                TEXT,
  icon_url                TEXT,
  created_at              DATETIME DEFAULT NOW(),

  UNIQUE(name),
  UNIQUE(domain_name),
  PRIMARY KEY(id)
) ENGINE = InnoDB;
