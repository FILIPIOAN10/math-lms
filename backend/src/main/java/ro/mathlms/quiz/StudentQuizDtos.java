package ro.mathlms.quiz;

import ro.mathlms.enchapter.EnChapterLinkDto;
import ro.mathlms.enchapter.LessonLinks;
import ro.mathlms.user.ExtraTime;

import java.time.Instant;
import java.util.List;

/**
 * Student-facing quiz DTOs. Unlike {@link QuizDtos} (the admin view), these deliberately
 * <strong>omit the correct answer and the barem</strong>: an option carries no {@code correct}
 * flag and an item carries no {@code solution}. This is the anti-cheat boundary — the right
 * answer never leaves the server before the attempt is submitted and graded.
 */
public final class StudentQuizDtos {

    private StudentQuizDtos() {
    }

    /** One answer choice as the student sees it — no {@code correct} flag. */
    public record StudentOptionDto(Long id, int position, String text) {
        public static StudentOptionDto from(QuizOption option) {
            return new StudentOptionDto(option.getId(), option.getPosition(), option.getText());
        }
    }

    /** One item as the student sees it — no barem/solution. Options empty for OPEN items. */
    public record StudentItemDto(
            Long id,
            int position,
            QuizItemType type,
            String statement,
            int points,
            List<StudentOptionDto> options,
            int hintCount, // hints this item has - only ever > 0 in practice mode; a graded test hides even their existence
            String section, // the heading it sits under ("SUBIECTUL I"); null = none
            String image, // the picture under the statement: a version token for its URL, or null
            List<PartDtos.StudentPartDto> parts // requirements a), b)... of an open problem (no barem); empty = none
    ) {
        public static StudentItemDto from(QuizItem item, List<QuizOption> options, int hintCount) {
            return from(item, options, hintCount, List.of());
        }

        public static StudentItemDto from(QuizItem item, List<QuizOption> options, int hintCount,
                                          List<QuizItemPart> parts) {
            return new StudentItemDto(
                    item.getId(),
                    item.getPosition(),
                    item.getType(),
                    item.getStatement(),
                    item.getPoints(),
                    options.stream().map(StudentOptionDto::from).toList(),
                    hintCount,
                    item.getSection(),
                    item.getImageKey(),
                    PartDtos.StudentPartDto.of(parts));
        }
    }

    /** A published quiz ready to be taken. */
    /**
     * What a student sees BEFORE starting: enough to know what they are walking into (how many items, how many
     * points, whether a clock will run) without revealing a single statement.
     */
    public record QuizPreviewDto(
            Long id,
            String title,
            String description,
            Integer timeLimitMinutes, // null = untimed; this student's minutes, extra time included
            boolean practiceAllowed,
            int itemCount,
            int maxScore,
            Integer maxAttempts, // null = no limit
            int attemptsUsed,
            RetakeBlock blockedBy, // null = the student may start it
            OpenAttemptDto openTest, // null = no test to resume
            OpenAttemptDto openPractice, // null = no practice to resume
            int photoItemCount, // questions solved on paper and photographed: the student needs paper and a camera
            int extraTimePercent // the student's extra time already in timeLimitMinutes (0 = none), so the screen can say so
    ) {}

    /**
     * A test or practice the student started and did not hand in, as the screen before it tells it: how many questions
     * they answered and, when timed, when it closes ({@code serverNow} so the browser counts from the server's clock).
     */
    public record OpenAttemptDto(int answered, Instant deadlineAt, Instant serverNow) {
    }

    /** A quiz on the student's list, with what they may still do with it (see {@link AttemptAllowance}). */
    public record OfferedQuizDto(
            Long id,
            String title,
            String description,
            QuizStatus status,
            Long schoolClassId,
            String schoolClassName,
            Integer timeLimitMinutes,
            boolean practiceAllowed,
            Long chapterId,
            Integer maxAttempts,
            int attemptsUsed,
            RetakeBlock blockedBy
    ) {
        /** {@code extraTimePercent}: the student's own extra time, so the card shows the minutes they really get. */
        public static OfferedQuizDto of(Quiz quiz, AttemptAllowance allowance, int extraTimePercent) {
            QuizDtos.QuizSummaryDto summary = QuizDtos.QuizSummaryDto.from(quiz);
            return new OfferedQuizDto(summary.id(), summary.title(), summary.description(), summary.status(),
                    summary.schoolClassId(), summary.schoolClassName(),
                    ExtraTime.minutesFor(summary.timeLimitMinutes(), extraTimePercent),
                    summary.practiceAllowed(), summary.chapterId(), allowance.maxAttempts(), // with their own extra ones
                    allowance.used(), allowance.blockedBy());
        }
    }

    public record StudentQuizDto(
            Long id,
            String title,
            String description,
            Integer timeLimitMinutes, // null = untimed
            List<StudentItemDto> items
    ) {
        public static StudentQuizDto of(Quiz quiz, List<StudentItemDto> items, int extraTimePercent) {
            return new StudentQuizDto(quiz.getId(), quiz.getTitle(), quiz.getDescription(),
                    ExtraTime.minutesFor(quiz.getTimeLimitMinutes(), extraTimePercent), items);
        }
    }

    /**
     * What a PRACTICE answer reveals at once: whether it was right ({@code null} for an open item, which a teacher
     * would mark), the id of the right option, the barem, and the teacher's words about the wrong option chosen (null =
     * none). Never produced for a graded TEST.
     */
    public record AnswerFeedbackDto(Boolean correct, Long correctOptionId, String solution, String mistake,
                                    String correctNumber, // NUMERIC only: the right answer as the teacher wrote it
                                    // The lesson to revise: after a wrong answer, or beside the barem of an open problem.
                                    EnChapterLinkDto enChapter,
                                    List<PartLessonDto> partLessons // an open problem with requirements: one per a), b)...
    ) {
        public AnswerFeedbackDto(Boolean correct, Long correctOptionId, String solution) {
            this(correct, correctOptionId, solution, null, null, null, List.of());
        }
    }

    /** The lesson of one requirement of an open problem: "a" and the chapter it points at. */
    public record PartLessonDto(String label, EnChapterLinkDto enChapter) {
    }

    /** The class chapter a question belongs to, with what a link to its exercises needs. */
    public record ChapterLinkDto(Long id, String title, Long bookId, Long classId) {
    }

    /** One revealed hint: its number (1-based), its text, and how many hints the item has in all. */
    public record HintDto(int number, String text, int total) {
    }

    /** The hints a resumed practice has already revealed for one item, in order. */
    public record RevealedHintsDto(Long itemId, List<String> hints) {
    }

    /**
     * What the student already saved on an in-progress attempt, so a resumed page can restore it.
     * Only the student's own choice (option id / the photos sent) — never whether it is correct.
     */
    public record SavedAnswerDto(Long itemId, Long selectedOptionId, boolean photoUploaded,
                                 AnswerFeedbackDto feedback, // PRACTICE only; always null in a graded TEST
                                 List<Long> photoIds,        // S2: the pages sent, oldest first
                                 String numericAnswer,       // the number typed; null = none
                                 java.util.Map<Long, String> partAnswers // V72: the final result typed per requirement, by part id
    ) {
        public static SavedAnswerDto from(ItemResponse response) {
            return from(response, null);
        }

        public static SavedAnswerDto from(ItemResponse response, AnswerFeedbackDto feedback) {
            return new SavedAnswerDto(
                    response.getItem().getId(),
                    response.getSelectedOption() == null ? null : response.getSelectedOption().getId(),
                    response.hasPhotos(),
                    feedback,
                    response.photoIds(),
                    response.getNumericAnswer(),
                    java.util.Map.copyOf(response.getPartAnswers()));
        }
    }

    /** S2: an answer's photos after one was added or deleted (oldest first), with the practice feedback (null in a test). */
    public record PhotosDto(List<Long> photoIds, AnswerFeedbackDto feedback) {}

    /**
     * The attempt the student is now working on, the answer-hidden quiz to fill in, and the answers
     * already saved (empty for a fresh attempt).
     */
    public record StartedAttemptDto(Long attemptId, QuizAttemptStatus status, StudentQuizDto quiz,
                                    List<SavedAnswerDto> answers,
                                    Instant deadlineAt, // null = untimed
                                    Instant serverNow,  // the server's clock, so the browser can show the right countdown despite a skewed own clock
                                    AttemptMode mode,
                                    List<RevealedHintsDto> revealedHints // practice only; empty in a graded test
    ) {
    }

    /** One point of the progress chart: how a graded attempt went, as points and as a percent of the quiz's max. */
    public record ProgressPointDto(
            Long attemptId,
            Long quizId,
            String quizTitle,
            Instant submittedAt,
            int score,
            int maxScore,
            int percent
    ) {
    }

    /** One row of the student's own attempt history ("Încercările mele"). */
    public record MyAttemptDto(
            Long attemptId,
            Long quizId,
            String quizTitle,
            QuizAttemptStatus status,
            Instant startedAt,
            Instant submittedAt,
            Integer score,
            AttemptMode mode,
            int maxScore,       // the quiz's total points, so a score reads "8 / 11"
            boolean newResult,  // S3: graded (or newly commented) and not yet opened by the student
            // When this open timed test closes - the clock runs while the student is away and the paper is then handed
            // in on its own; null = untimed or already handed in. serverNow lets the browser count from the server's clock.
            Instant deadlineAt,
            Instant serverNow
    ) {
        /** Without the clock (an untimed or handed-in attempt). */
        public MyAttemptDto(Long attemptId, Long quizId, String quizTitle, QuizAttemptStatus status, Instant startedAt,
                            Instant submittedAt, Integer score, AttemptMode mode, int maxScore, boolean newResult) {
            this(attemptId, quizId, quizTitle, status, startedAt, submittedAt, score, mode, maxScore, newResult, null, null);
        }

        public static MyAttemptDto from(QuizAttempt attempt, int maxScore, Instant now) {
            Instant deadline = attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS ? attempt.getDeadlineAt() : null;
            return new MyAttemptDto(attempt.getId(), attempt.getQuiz().getId(), attempt.getQuiz().getTitle(),
                    attempt.getStatus(), attempt.getStartedAt(), attempt.getSubmittedAt(), attempt.getScore(),
                    attempt.getMode(), maxScore, attempt.hasUnseenResult(), deadline, now);
        }
    }

    /**
     * Outcome of submitting. {@code autoScore}/{@code autoMaxScore} cover the auto-graded
     * single-choice items; {@code finalScore} is non-null only once the whole attempt is graded
     * (i.e. the quiz had no open items awaiting a teacher).
     */
    public record AttemptResultDto(
            Long attemptId,
            QuizAttemptStatus status,
            int autoScore,
            int autoMaxScore,
            Integer finalScore
    ) {
    }

    /**
     * One item as shown on the student's result view (after submit). Now that grading is done the
     * correct option and the barem — hidden before submit — are revealed for learning.
     */
    public record ItemResultDto(
            Long itemId,        // lets the student's page fetch their own photo of an OPEN item
            int position,
            QuizItemType type,
            String statement,
            int points,
            Integer awardedPoints,
            Boolean correct,
            String selectedOptionText,
            String correctOptionText,
            String barem,
            boolean photoUploaded,
            int hintsUsed,      // practice only; 0 in a graded test
            int hintsAvailable, // practice only; 0 in a graded test
            String teacherComment,
            String section, // the heading it sits under; null = none
            String image, // the picture under the statement: a version token for its URL, or null
            List<Long> photoIds, // S2: the pages of an OPEN answer, oldest first (only the student's own API serves them)
            List<PartDtos.GradedPartDto> parts, // requirements a), b)... with their barem and points; empty = none
            String selectedOptionLetter, // "a", "b"... as on the paper test; null = no answer / not a grilă
            String correctOptionLetter,
            EnChapterLinkDto enChapter, // the EN chapter page to open when points were lost; null = none
            String mistake, // the teacher's words about the wrong option chosen; null = right, or none written
            ChapterLinkDto chapter, // the class chapter with exercises to practise; null = none
            LessonLinks.LessonLinkDto mistakeLesson, // the lesson that explanation comes from (V70); null = none
            boolean explanationRequested // „De ce am greșit?" asked and waiting for the teacher
    ) {}

    /** The student's graded attempt: the score plus a per-item breakdown. */
    public record AttemptResultViewDto(
            Long attemptId,
            String quizTitle,
            QuizAttemptStatus status,
            Integer finalScore,
            int maxScore,
            List<ItemResultDto> items,
            AttemptMode mode,
            String teacherComment, // on the whole paper
            Long quizId,
            boolean practiceAllowed, // S4: whether "Exersează ce ai greșit" can open a practice of this quiz
            // A retake is still left: right or wrong only - no right option, numeric answer, barem nor note on a wrong
            // option until the last attempt (see AnswerKeys)
            boolean answerKeyHidden
    ) {}
}
