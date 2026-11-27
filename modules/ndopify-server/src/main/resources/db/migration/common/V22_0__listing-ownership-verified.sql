-- #392: the owner-verification fact, frozen when the publication gate saves the listing (#297/#299). Every existing
-- row reads false: a listing published before the gate has no frozen fact, never a backfill, never un-badged.
ALTER TABLE T_LISTING ADD COLUMN ownership_verified BOOLEAN NOT NULL DEFAULT false;
