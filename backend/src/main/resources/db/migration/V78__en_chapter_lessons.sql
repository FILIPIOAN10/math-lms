-- A NEW lesson per EN chapter (plan step 5), written by tutor.synthesize from all the chapter's sources and checked
-- claim by claim by tutor.verify_lesson; stored by tutor.store_lesson. Students read it only once the teacher has
-- approved it AND shown the chapter (en_chapters.lesson_published); the book pages stay below it as the source.
CREATE TABLE en_chapter_lessons (
    chapter_id        BIGINT      PRIMARY KEY REFERENCES en_chapters (id) ON DELETE CASCADE,
    -- What a student reads: Markdown with $LaTeX$ and the book's figures as ![..](poza:..).
    markdown          TEXT        NOT NULL,
    -- For the teacher's review, as JSON text: [{id, where, text, colour, reasons, sourceIds}] in reading order, and
    -- the sources those claims cite [{id, kind, label, text}].
    claims_json       TEXT        NOT NULL,
    sources_json      TEXT        NOT NULL,
    -- flagged = a red claim is left (it cannot be approved); draft = none; approved = the teacher approved it.
    status            VARCHAR(10) NOT NULL CHECK (status IN ('draft', 'flagged', 'approved')),
    red_count         INT         NOT NULL DEFAULT 0,
    yellow_count      INT         NOT NULL DEFAULT 0,
    -- The teacher changed the text: a later generation never replaces it, and the claim colours no longer describe it.
    edited_by_teacher BOOLEAN     NOT NULL DEFAULT FALSE,
    generated_md5     TEXT,
    model             VARCHAR(60),
    approved_by       BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    approved_at       TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every foreign key has its index (SchemaIndexesTest): deleting a user looks here.
CREATE INDEX idx_en_chapter_lessons_approved_by ON en_chapter_lessons (approved_by);
