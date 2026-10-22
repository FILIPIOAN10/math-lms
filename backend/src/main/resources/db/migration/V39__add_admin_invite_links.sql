-- Scoatem constrângerile vechi
ALTER TABLE invite_links DROP CONSTRAINT ck_invite_links_kind;
ALTER TABLE invite_links DROP CONSTRAINT ck_invite_links_target;

-- Le punem înapoi, adăugând 'ADMIN'
ALTER TABLE invite_links ADD CONSTRAINT ck_invite_links_kind
    CHECK (kind IN ('CLASS', 'PARENT', 'ADMIN'));

ALTER TABLE invite_links ADD CONSTRAINT ck_invite_links_target CHECK (
    (kind = 'CLASS' AND school_class_id IS NOT NULL AND student_id IS NULL)
    OR (kind = 'PARENT' AND student_id IS NOT NULL AND school_class_id IS NULL)
    OR (kind = 'ADMIN' AND school_class_id IS NULL AND student_id IS NULL)
);