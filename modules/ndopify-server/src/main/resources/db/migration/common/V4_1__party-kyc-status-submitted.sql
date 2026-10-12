-- KycStatus is stored by ordinal. SUBMITTED was inserted after PENDING, which shifts every later value by one:
--   IN_PROGRESS 2 -> 3, VERIFIED 3 -> 4, REJECTED 4 -> 5, REQUIRES_MANUAL_REVIEW 5 -> 6, CANCELLED 6 -> 7.
-- UNKNOWN (0) and PENDING (1) are unchanged, and no row is SUBMITTED yet. modified_at is kept: this only
-- renumbers, nothing about the rows changed.

UPDATE T_PARTY SET kyc_status = kyc_status + 1, modified_at = modified_at WHERE kyc_status BETWEEN 2 AND 6;
UPDATE T_KYC_CASE SET status = status + 1, modified_at = modified_at WHERE status BETWEEN 2 AND 6;
UPDATE T_KYC_VERIFICATION SET status = status + 1, modified_at = modified_at WHERE status BETWEEN 2 AND 6;
