-- #369: the kind of files a document was created from (images, or one PDF rendered to image pages). Nullable so that
-- every existing row reads UNKNOWN and accepts either kind on a page-level write.
ALTER TABLE T_MANDATE_DOCUMENT ADD COLUMN source VARCHAR(30) DEFAULT NULL;
