-- „Teme recapitulative”: the exercise collection (culegere) solved twice by AI outside the app and imported from a zip.
-- Nothing in it reaches a student until a teacher approves it and puts it in a test (published, given as homework).
CREATE TABLE recap_exercises (
    id              BIGSERIAL PRIMARY KEY,
    -- "T8-19": tema 8, exercise 19 of the book; the import adds only keys it has not seen, so it can be run again.
    source_key      VARCHAR(40)  NOT NULL,
    tema            INTEGER      NOT NULL,
    number          INTEGER      NOT NULL,
    statement       TEXT         NOT NULL,
    -- the first AI run's steps (the barem), the answer in words and, when both runs agree on a plain number, the
    -- result the student types (exercises without requirements a), b)...)
    solution        TEXT,
    answer          TEXT,
    result          VARCHAR(40),
    -- AGREE / PARTIAL / PROOF / NEEDS_FIGURE / UNPARSED: what comparing the two AI runs said
    ai_status       VARCHAR(20)  NOT NULL,
    image_key       VARCHAR(200),
    -- the book has a drawing that was not found: the exercise cannot be approved until one is added or waived
    figure_missing  BOOLEAN      NOT NULL DEFAULT FALSE,
    en_chapter_id   BIGINT REFERENCES en_chapters (id) ON DELETE SET NULL,
    review          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    reviewed_by     BIGINT REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_recap_exercises_key UNIQUE (source_key),
    CONSTRAINT ck_recap_exercises_review CHECK (review IN ('DRAFT', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_recap_exercises_chapter ON recap_exercises (en_chapter_id, review);
CREATE INDEX idx_recap_exercises_reviewed_by ON recap_exercises (reviewed_by);

-- The requirements a), b)... of an exercise, in order, each with its barem, answer and (when checked) typed result.
CREATE TABLE recap_exercise_parts (
    exercise_id BIGINT       NOT NULL REFERENCES recap_exercises (id) ON DELETE CASCADE,
    position    INTEGER      NOT NULL,
    statement   TEXT         NOT NULL,
    solution    TEXT,
    answer      TEXT,
    result      VARCHAR(40),
    verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (exercise_id, position)
);

-- Which tests an exercise was put in: "already used" in the list, and catch-up homework picks unused ones first.
CREATE TABLE recap_exercise_quizzes (
    exercise_id BIGINT NOT NULL REFERENCES recap_exercises (id) ON DELETE CASCADE,
    quiz_id     BIGINT NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    PRIMARY KEY (exercise_id, quiz_id)
);

CREATE INDEX idx_recap_exercise_quizzes_quiz ON recap_exercise_quizzes (quiz_id);
