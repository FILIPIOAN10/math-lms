-- „Ținta la EN" and the weekly study plan. The plan itself is not stored: it is worked out on every request from the
-- student's level per EN chapter, so it follows their answers. Stored here is only what it is worked out from.

-- The EN exam date, one for the whole school, set by an admin on the EN chapters page. A single row (id = 1); NULL
-- until it is set, and until then there is no plan.
CREATE TABLE en_exam_settings (
    id         SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    exam_date  DATE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO en_exam_settings (id) VALUES (1);

-- The grade a student aims for. `baseline_grade` is their estimate on `set_on`, the day they set (or last changed) the
-- target - NULL when there was no estimate yet. The teacher's „în urmă" compares today's estimate with the straight
-- line from that point to the target on the exam day.
CREATE TABLE student_en_goals (
    student_id     BIGINT       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    target_grade   NUMERIC(4,2) NOT NULL CHECK (target_grade BETWEEN 1 AND 10),
    baseline_grade NUMERIC(4,2) CHECK (baseline_grade BETWEEN 1 AND 10),
    set_on         DATE         NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

-- The estimated grade, one row per student per day (Europe/Bucharest days), for the chart against the target. Written
-- by the daily job (app.en-snapshots.enabled) and when a student sets a target; a second write the same day replaces it.
CREATE TABLE en_prediction_snapshots (
    student_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day        DATE         NOT NULL,
    grade      NUMERIC(4,2) NOT NULL CHECK (grade BETWEEN 1 AND 10),
    low        NUMERIC(4,2) NOT NULL CHECK (low BETWEEN 1 AND 10),
    high       NUMERIC(4,2) NOT NULL CHECK (high BETWEEN 1 AND 10),
    PRIMARY KEY (student_id, day)
);

-- A chapter the teacher put into a student's plan: it comes first, whatever its gain. `pinned_by` is NULL once that
-- teacher's account is gone; the pin stays.
CREATE TABLE study_plan_pins (
    student_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    en_chapter_id BIGINT      NOT NULL REFERENCES en_chapters (id) ON DELETE CASCADE,
    pinned_by     BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (student_id, en_chapter_id)
);
CREATE INDEX idx_study_plan_pins_chapter ON study_plan_pins (en_chapter_id);
CREATE INDEX idx_study_plan_pins_pinned_by ON study_plan_pins (pinned_by);
