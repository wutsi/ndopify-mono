-- A user is a global identity; its tenants are its memberships. Additive only: the replaced columns
-- (T_USER.tenant_id, T_USER.party_id, T_USER_APPLICATION.user_id) are no longer written and are dropped later.
CREATE TABLE T_MEMBERSHIP(
  id                     BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id              BIGINT NOT NULL,

  user_id                BIGINT NOT NULL,
  party_id               BIGINT,

  deleted                BOOLEAN NOT NULL DEFAULT false,
  created_at             DATETIME DEFAULT NOW(),

  UNIQUE(tenant_id, user_id),
  UNIQUE(party_id),

  CONSTRAINT FK_MEMBERSHIP_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  CONSTRAINT FK_MEMBERSHIP_user FOREIGN KEY(user_id) REFERENCES T_USER(id),
  CONSTRAINT FK_MEMBERSHIP_party FOREIGN KEY(party_id) REFERENCES T_PARTY(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

INSERT INTO T_MEMBERSHIP(tenant_id, user_id, party_id, created_at)
  SELECT tenant_id, id, party_id, created_at FROM T_USER WHERE tenant_id IS NOT NULL;

ALTER TABLE T_USER_APPLICATION ADD COLUMN membership_id BIGINT;
ALTER TABLE T_USER_APPLICATION ADD CONSTRAINT FK_USER_APPLICATION_membership FOREIGN KEY(membership_id) REFERENCES T_MEMBERSHIP(id);
ALTER TABLE T_USER_APPLICATION ADD CONSTRAINT UQ_USER_APPLICATION_membership_application UNIQUE(membership_id, application_id);
ALTER TABLE T_USER_APPLICATION MODIFY user_id BIGINT NULL;

UPDATE T_USER_APPLICATION ua JOIN T_MEMBERSHIP m ON m.user_id = ua.user_id
  SET ua.membership_id = m.id;
