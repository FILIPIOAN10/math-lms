package ro.mathlms.assignment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.assignment.AssignmentDtos.AssignmentSummaryDto;
import ro.mathlms.assignment.AssignmentDtos.StudentAssignmentDto;
import ro.mathlms.assignment.AssignmentDtos.StudentStatusDto;
import ro.mathlms.assignment.AssignmentProgress.State;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.outbox.OutboxEventPublisher;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.QuizStatus;
import ro.mathlms.user.ExtraTime;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Homework: assign an existing quiz to a class with a due date, show the teacher how the class is doing and each student
 * their own list, and queue one "due soon" reminder per assignment for the students who have not handed in. A student's
 * progress is never stored - it is derived from their graded-test attempts (see {@link AssignmentProgress}).
 */
@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentOverrideRepository overrideRepository;
    private final QuizRepository quizRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final OutboxEventPublisher publisher;
    private final AuditLog auditLog;
    private final Clock clock;
    private final int reminderLeadHours;
    private final boolean remindParents;

    public AssignmentService(AssignmentRepository assignmentRepository, AssignmentOverrideRepository overrideRepository,
                             QuizRepository quizRepository,
                             SchoolClassRepository schoolClassRepository, EnrollmentRepository enrollmentRepository,
                             QuizAttemptRepository attemptRepository, UserRepository userRepository,
                             OutboxEventPublisher publisher, AuditLog auditLog, Clock clock,
                             @Value("${app.assignments.reminder-lead-hours:24}") int reminderLeadHours,
                             @Value("${app.assignments.remind-parents:true}") boolean remindParents) {
        this.auditLog = auditLog;
        this.assignmentRepository = assignmentRepository;
        this.overrideRepository = overrideRepository;
        this.quizRepository = quizRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
        this.publisher = publisher;
        this.clock = clock;
        this.reminderLeadHours = reminderLeadHours;
        this.remindParents = remindParents;
    }

    // ---------------------------------------------------------------- teacher: manage

    /**
     * Assigns a PUBLISHED quiz to a class. A quiz restricted to another class cannot be handed to this one, the deadline
     * must be in the future, and one quiz is assigned to a class at most once (move the deadline instead).
     */
    @Transactional
    public Assignment create(Long quizId, Long schoolClassId, Instant dueAt) {
        return create(quizId, schoolClassId, dueAt, null);
    }

    /**
     * The same, released only at {@code availableFrom} (null = now): see {@link #requireRelease}. Only the homework
     * waits for the release - the test is already published, so students who see it keep seeing it.
     */
    @Transactional
    public Assignment create(Long quizId, Long schoolClassId, Instant dueAt, Instant availableFrom) {
        return create(quizId, schoolClassId, dueAt, availableFrom, false);
    }

    /**
     * {@code hidesQuiz}: the test is being published for this homework (see {@link PublishAndAssignService}), so with a
     * release it stays hidden from every student until then (V66). Ignored without a release.
     */
    @Transactional
    public Assignment create(Long quizId, Long schoolClassId, Instant dueAt, Instant availableFrom, boolean hidesQuiz) {
        Assignment assignment = build(quizId, schoolClassId, dueAt, false, availableFrom);
        if (hidesQuiz && availableFrom != null) {
            assignment.hideQuizUntilRelease();
        }
        Assignment saved = assignmentRepository.save(assignment);
        auditLog.record(AuditEvent.of(AuditAction.HOMEWORK_ASSIGNED, saved.getId(), label(saved))
                .details("termen " + AuditText.dateTime(dueAt) + released(availableFrom)
                        + (saved.isHidesQuiz() ? ", testul ascuns tuturor până atunci" : "")));
        return saved;
    }

    /** ", apare 14.11.2026 08:00" for the journal when the homework is scheduled, else nothing. */
    private static String released(Instant availableFrom) {
        return availableFrom == null ? "" : ", apare " + AuditText.dateTime(availableFrom);
    }

    /**
     * A new, unsaved homework after every check of {@link #create}. A catch-up one skips "already given to this class":
     * re-giving a test the class had is what it is for.
     */
    private Assignment build(Long quizId, Long schoolClassId, Instant dueAt, boolean catchUp, Instant availableFrom) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new QuizNotFoundException("Quiz", quizId));
        SchoolClass schoolClass = schoolClassRepository.findById(schoolClassId)
                .orElseThrow(() -> new QuizNotFoundException("SchoolClass", schoolClassId));
        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new InvalidAssignmentException("Doar un test publicat poate fi dat ca temă");
        }
        if (quiz.getSchoolClass() != null && !quiz.getSchoolClass().getId().equals(schoolClassId)) {
            throw new InvalidAssignmentException("Testul este destinat altei clase decât cea aleasă");
        }
        requireFuture(dueAt);
        if (!catchUp && assignmentRepository.existsByQuizIdAndSchoolClassIdAndCatchUpFalse(quizId, schoolClassId)) {
            throw new AssignmentConflictException("Testul este deja dat ca temă acestei clase — modifică termenul");
        }
        requireRelease(availableFrom, dueAt);
        Assignment assignment = new Assignment(quiz, schoolClass, dueAt, Instant.now(clock));
        assignment.scheduleRelease(availableFrom);
        return assignment;
    }

    /** A scheduled release must be in the future (else it is simply "now") and before the deadline. */
    private void requireRelease(Instant availableFrom, Instant dueAt) {
        if (availableFrom == null) {
            return;
        }
        if (!availableFrom.isAfter(Instant.now(clock))) {
            throw new InvalidAssignmentException("Data de la care apare tema trebuie să fie în viitor");
        }
        if (!availableFrom.isBefore(dueAt)) {
            throw new InvalidAssignmentException("Tema trebuie să apară înainte de termen");
        }
    }

    /**
     * The same homework for several classes in one go (Canvas: one assignment, several sections), each class its own
     * {@link Assignment} with the same deadline. All or nothing: one refused class (already given, another class's quiz)
     * leaves none of them created. A class named twice counts once.
     */
    @Transactional
    public List<Assignment> createForClasses(Long quizId, List<Long> schoolClassIds, Instant dueAt) {
        return createForClasses(quizId, schoolClassIds, dueAt, null);
    }

    @Transactional
    public List<Assignment> createForClasses(Long quizId, List<Long> schoolClassIds, Instant dueAt,
                                             Instant availableFrom) {
        List<Long> ids = schoolClassIds == null ? List.of() : schoolClassIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            throw new InvalidAssignmentException("Alege cel puțin o clasă");
        }
        return ids.stream().map(classId -> create(quizId, classId, dueAt, availableFrom)).toList();
    }

    /**
     * Catch-up homework: the quiz for some students of the class only - the rules of {@link #create} plus at least one
     * student, each an enrolled, not erased student of that class - but a test the class already had may be given again
     * (the chosen students redo it: only attempts started from now on count, and their attempt limit starts again).
     * Named twice counts once. One journal row, saying how many students (not who: the list is on the homework itself).
     */
    @Transactional
    public Assignment createForStudents(Long quizId, Long schoolClassId, List<Long> studentIds, Instant dueAt) {
        return createForStudents(quizId, schoolClassId, studentIds, dueAt, null);
    }

    @Transactional
    public Assignment createForStudents(Long quizId, Long schoolClassId, List<Long> studentIds, Instant dueAt,
                                        Instant availableFrom) {
        List<Long> ids = studentIds == null ? List.of() : studentIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            throw new InvalidAssignmentException("Alege cel puțin un elev");
        }
        Set<Long> roster = enrollmentRepository.findBySchoolClassIdFetchStudent(schoolClassId).stream()
                .map(Enrollment::getStudent)
                .filter(student -> !student.isErased())
                .map(User::getId)
                .collect(Collectors.toSet());
        if (!roster.containsAll(ids)) {
            throw new InvalidAssignmentException("Unii dintre elevii aleși nu sunt în clasă");
        }
        Assignment assignment = build(quizId, schoolClassId, dueAt, true, availableFrom);
        assignment.restrictTo(ids);
        Assignment saved = assignmentRepository.save(assignment);
        auditLog.record(AuditEvent.of(AuditAction.HOMEWORK_ASSIGNED, saved.getId(), label(saved))
                .details("termen " + AuditText.dateTime(dueAt) + released(availableFrom) + ", doar pentru " + ids.size()
                        + (ids.size() == 1 ? " elev" : " elevi")));
        return saved;
    }

    @Transactional
    public Assignment reschedule(Long id, Instant dueAt) {
        Assignment assignment = require(id);
        requireFuture(dueAt);
        if (assignment.getAvailableFrom() != null && !assignment.getAvailableFrom().isBefore(dueAt)) {
            throw new InvalidAssignmentException("Termenul trebuie să fie după data la care apare tema");
        }
        Instant before = assignment.getDueAt();
        assignment.reschedule(dueAt);
        Assignment saved = assignmentRepository.save(assignment);
        if (!before.equals(dueAt)) {
            auditLog.record(AuditEvent.of(AuditAction.HOMEWORK_RESCHEDULED, id, label(assignment))
                    .details("termen " + AuditText.dateTime(before) + " → " + AuditText.dateTime(dueAt)));
        }
        return saved;
    }

    /**
     * Moves a scheduled homework's release, or gives it now ({@code availableFrom} null). Only while it is still hidden:
     * students who already saw it must not have it taken away again. One journal row per real change.
     */
    @Transactional
    public Assignment setRelease(Long id, Instant availableFrom) {
        Assignment assignment = require(id);
        Instant now = Instant.now(clock);
        if (assignment.isOpen(now)) {
            throw new InvalidAssignmentException("Tema e deja vizibilă elevilor");
        }
        requireRelease(availableFrom, assignment.getDueAt());
        Instant before = assignment.getAvailableFrom();
        assignment.scheduleRelease(availableFrom);
        Assignment saved = assignmentRepository.save(assignment);
        if (!Objects.equals(before, availableFrom)) {
            auditLog.record(AuditEvent.of(AuditAction.HOMEWORK_RESCHEDULED, id, label(assignment))
                    .details("apare " + AuditText.dateTime(before) + " → "
                            + (availableFrom == null ? "acum" : AuditText.dateTime(availableFrom))));
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Assignment assignment = require(id);
        assignmentRepository.delete(assignment);
        auditLog.record(AuditEvent.of(AuditAction.HOMEWORK_DELETED, id, label(assignment)));
    }

    /**
     * One student's exception to the homework (Canvas "Assign To", Google Classroom "Excused"): their own deadline
     * ({@code dueAt}, null = the class one) and/or excused - the homework does not count for them. Neither removes the
     * exception. Only a student of the homework's class; an own deadline must be in the future. One journal row per
     * real change ("termenul clasei → scutit"), none when nothing changes.
     */
    @Transactional
    public void setOverride(Long assignmentId, Long studentId, Instant dueAt, boolean excused) {
        Assignment assignment = assignmentRepository.findByIdFetched(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException("Nu există tema " + assignmentId));
        User student = userRepository.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT && !u.isErased())
                .filter(u -> enrollmentRepository.existsByStudentIdAndSchoolClassId(u.getId(),
                        assignment.getSchoolClass().getId()))
                .orElseThrow(() -> new InvalidAssignmentException("Elevul nu este în clasa acestei teme"));
        if (!assignment.isFor(student.getId())) { // catch-up homework given to other students of the class
            throw new InvalidAssignmentException("Tema nu este dată acestui elev");
        }
        if (dueAt != null) {
            requireFuture(dueAt);
        }
        AssignmentOverride existing = overrideRepository.findOne(assignmentId, studentId).orElse(null);
        String before = describe(existing);
        AssignmentOverride override = existing != null ? existing
                : new AssignmentOverride(assignment, student, Instant.now(clock));
        override.moveDeadline(dueAt);
        override.excuse(excused);
        String after = describe(override);
        if (before.equals(after)) {
            return;
        }
        if (override.isEmpty()) {
            if (existing != null) {
                overrideRepository.delete(existing);
            }
        } else {
            overrideRepository.save(override);
        }
        auditLog.record(AuditEvent.of(override.isEmpty() ? AuditAction.HOMEWORK_OVERRIDE_REMOVED
                        : AuditAction.HOMEWORK_OVERRIDE_SET, assignmentId, label(assignment))
                .about(student)
                .details(before + " → " + after));
    }

    /** "termenul clasei", "termen 12.10.2026 13:00", "scutit": what one student's exception says, for the journal. */
    private static String describe(AssignmentOverride override) {
        if (override == null || override.isEmpty()) {
            return "termenul clasei";
        }
        if (override.isExcused()) {
            return "scutit";
        }
        return "termen " + AuditText.dateTime(override.getDueAt());
    }

    /** "Teză · Clasa a 9-a": the quiz and the class it was handed to. */
    private static String label(Assignment assignment) {
        return assignment.getQuiz().getTitle() + " · " + assignment.getSchoolClass().getName();
    }

    /** Every homework with its counts, from the same few queries however many there are (see {@link #summarise}). */
    @Transactional(readOnly = true)
    public List<AssignmentSummaryDto> list() {
        return summarise(assignmentRepository.findAllFetched());
    }

    @Transactional(readOnly = true)
    public AssignmentSummaryDto summaryOf(Long id) {
        return summarise(List.of(assignmentRepository.findByIdFetched(id)
                .orElseThrow(() -> new AssignmentNotFoundException("Nu există tema " + id)))).get(0);
    }

    /** Every enrolled student (erased accounts excluded) with how far they got, in roster order. */
    @Transactional(readOnly = true)
    public List<StudentStatusDto> status(Long id) {
        Assignment assignment = assignmentRepository.findByIdFetched(id)
                .orElseThrow(() -> new AssignmentNotFoundException("Nu există tema " + id));
        List<User> students = rosterOf(assignment);
        Map<Long, AssignmentOverride> overrides = overridesOf(assignment);
        Map<Long, AssignmentProgress> progress = progressOf(assignment, students, overrides);
        return students.stream().map(student -> {
            AssignmentProgress p = progress.get(student.getId());
            AssignmentOverride o = overrides.get(student.getId());
            return new StudentStatusDto(student.getId(), student.getFullName(), p.state(), p.submittedAt(), p.late(),
                    p.score(), p.attemptId(), o == null ? null : o.getDueAt(), AssignmentOverride.isExcused(o));
        }).toList();
    }

    // ---------------------------------------------------------------- student: my homework

    /**
     * The homework of the student's classes: not yet handed in first (soonest deadline first), then the finished ones.
     * Each with the student's own deadline when they have one; homework they are excused from is left out unless handed in.
     */
    @Transactional(readOnly = true)
    public List<StudentAssignmentDto> myAssignments(String studentEmail) {
        return myAssignments(studentEmail, false);
    }

    /**
     * The same; {@code teacherView} keeps excused and not-yet-released homework too (flagged) - the teacher's page for
     * one student. A student or parent never sees a homework before its scheduled release.
     */
    @Transactional(readOnly = true)
    public List<StudentAssignmentDto> myAssignments(String studentEmail, boolean teacherView) {
        User student = userRepository.findByEmail(studentEmail).orElse(null);
        if (student == null || student.getRole() != Role.STUDENT) {
            return List.of();
        }
        List<Long> classIds = enrollmentRepository.findClassIdsByStudentId(student.getId());
        if (classIds.isEmpty()) {
            return List.of();
        }
        // An unpublished quiz cannot be started, so its homework is hidden until it is published again.
        Instant now = Instant.now(clock);
        List<Assignment> assignments = assignmentRepository.findBySchoolClassIdsFetched(classIds).stream()
                .filter(a -> a.getQuiz().getStatus() == QuizStatus.PUBLISHED)
                .filter(a -> a.isFor(student.getId())) // catch-up homework of their class, given to others
                .filter(a -> teacherView || a.isOpen(now))
                .toList();
        List<Long> quizIds = assignments.stream().map(a -> a.getQuiz().getId()).distinct().toList();
        Map<Long, List<QuizAttempt>> attemptsByQuiz = attemptRepository.findByStudentIdAndQuizIdIn(student.getId(), quizIds)
                .stream().collect(Collectors.groupingBy(a -> a.getQuiz().getId()));
        Map<Long, AssignmentOverride> overrides = overrideRepository.findByStudentId(student.getId()).stream()
                .collect(Collectors.toMap(o -> o.getAssignment().getId(), o -> o));

        return assignments.stream()
                .map(a -> {
                    AssignmentOverride o = overrides.get(a.getId());
                    Instant dueAt = AssignmentOverride.dueAtFor(a, o);
                    boolean excused = AssignmentOverride.isExcused(o);
                    AssignmentProgress p = AssignmentProgress.of(a,
                            attemptsByQuiz.getOrDefault(a.getQuiz().getId(), List.of()), dueAt);
                    return new Row(p, dueAt, new StudentAssignmentDto(a.getId(), a.getQuiz().getId(),
                            a.getQuiz().getTitle(), a.getSchoolClass().getName(), dueAt, p.state(), p.late(),
                            !excused && !p.isDone() && now.isAfter(dueAt), p.attemptId(),
                            ExtraTime.minutesFor(a.getQuiz().getTimeLimitMinutes(), student.getExtraTimePercent()),
                            excused, a.isOpen(now) ? null : a.getAvailableFrom()));
                })
                .filter(r -> teacherView || !r.dto().excused() || r.progress().isDone())
                .sorted(Comparator.<Row, Boolean>comparing(r -> r.progress().isDone() || r.dto().excused())
                        .thenComparing(Row::dueAt))
                .map(Row::dto)
                .toList();
    }

    private record Row(AssignmentProgress progress, Instant dueAt, StudentAssignmentDto dto) {
    }

    // ---------------------------------------------------------------- reminders

    /** Assignments whose deadline is within the lead time and whose reminder has not been queued yet. */
    @Transactional(readOnly = true)
    public List<Long> findDueForReminderIds() {
        Instant now = Instant.now(clock);
        return assignmentRepository.findIdsDueForReminder(now, now.plus(Duration.ofHours(reminderLeadHours)));
    }

    /**
     * Queues one reminder per student who has not handed in - and one for that student's linked parent, unless
     * {@code app.assignments.remind-parents=false} - inside this transaction (so the outbox rows commit with the
     * "reminder sent" mark). Re-checked under the row lock: a second run, another instance, an assignment moved out of the
     * window or one already reminded is a no-op. An assignment with nobody pending is still marked, so it is not retried.
     */
    @Transactional
    public void queueReminders(Long id) {
        Assignment assignment = assignmentRepository.findByIdForUpdate(id).orElse(null);
        Instant now = Instant.now(clock);
        if (assignment == null || assignment.getReminderSentAt() != null
                || assignment.getQuiz().getStatus() != QuizStatus.PUBLISHED // not marked: a republish still reminds
                || !assignment.isOpen(now) // not marked either: reminded once it is released
                || !assignment.getDueAt().isAfter(now)
                || assignment.getDueAt().isAfter(now.plus(Duration.ofHours(reminderLeadHours)))) {
            return;
        }
        List<User> students = rosterOf(assignment);
        // A student with an exception is not reminded of the class deadline: excused, or working to a date of their own
        // (reminded of that one by queueOwnDeadlineReminder).
        Map<Long, AssignmentOverride> overrides = overridesOf(assignment);
        Map<Long, AssignmentProgress> progress = progressOf(assignment, students, overrides);
        for (User student : students) {
            if (!overrides.containsKey(student.getId()) && !progress.get(student.getId()).isDone()) {
                publishReminder(assignment, student);
            }
        }
        assignment.markReminderSent(now);
        assignmentRepository.save(assignment);
    }

    /** Students' own deadlines within the lead time whose reminder has not been queued yet (see V63). */
    @Transactional(readOnly = true)
    public List<Long> findOwnDeadlinesDueForReminderIds() {
        Instant now = Instant.now(clock);
        return overrideRepository.findIdsDueForReminder(now, now.plus(Duration.ofHours(reminderLeadHours)));
    }

    /**
     * Queues the reminder of one student's own deadline (audit M2: the class reminder leaves them out), and their
     * parent's copy like the class one. Re-checked under the row lock: excused, moved out of the window, already
     * reminded or the quiz unpublished is a no-op; a student no longer given the homework (erased, left the class, not
     * a catch-up homework's student) or who handed in gets nothing but is marked, so it is not retried. A new date of
     * their own re-arms it.
     */
    @Transactional
    public void queueOwnDeadlineReminder(Long overrideId) {
        AssignmentOverride override = overrideRepository.findByIdForUpdate(overrideId).orElse(null);
        Instant now = Instant.now(clock);
        if (override == null || override.getReminderSentAt() != null || override.isExcused()
                || override.getDueAt() == null || !override.getDueAt().isAfter(now)
                || override.getDueAt().isAfter(now.plus(Duration.ofHours(reminderLeadHours)))) {
            return;
        }
        Assignment assignment = assignmentRepository.findByIdFetched(override.getAssignment().getId()).orElse(null);
        if (assignment == null || assignment.getQuiz().getStatus() != QuizStatus.PUBLISHED || !assignment.isOpen(now)) {
            return; // not marked: a republish (or the release) still reminds
        }
        User student = override.getStudent();
        boolean given = !student.isErased() && assignment.isFor(student.getId())
                && enrollmentRepository.existsByStudentIdAndSchoolClassId(student.getId(),
                assignment.getSchoolClass().getId());
        if (given && !AssignmentProgress.of(assignment, attemptRepository.findForAssignment(
                assignment.getQuiz().getId(), List.of(student.getId()), assignment.opensAt())).isDone()) {
            publishReminder(assignment, student);
        }
        override.markReminderSent(now);
        overrideRepository.save(override);
    }

    /** One "due soon" email for the student, and one for their linked parent unless parents are not reminded. */
    private void publishReminder(Assignment assignment, User student) {
        publisher.publish(OutboxEventTypes.ASSIGNMENT_REMINDER_EMAIL,
                new AssignmentReminderPayload(assignment.getId(), student.getId()));
        if (remindParents && student.getParent() != null && !student.getParent().isErased()) {
            publisher.publish(OutboxEventTypes.ASSIGNMENT_REMINDER_EMAIL, new AssignmentReminderPayload(
                    assignment.getId(), student.getId(), AssignmentReminderPayload.Recipient.PARENT));
        }
    }

    // ---------------------------------------------------------------- helpers

    private Assignment require(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new AssignmentNotFoundException("Nu există tema " + id));
    }

    private void requireFuture(Instant dueAt) {
        if (dueAt == null || !dueAt.isAfter(Instant.now(clock))) {
            throw new InvalidAssignmentException("Termenul trebuie să fie în viitor");
        }
    }

    /**
     * Who the homework is for, as users: the class roster - or, for catch-up homework, the chosen students still in it -
     * erased (anonymised) accounts left out.
     */
    private List<User> rosterOf(Assignment assignment) {
        return enrollmentRepository.findBySchoolClassIdFetchStudent(assignment.getSchoolClass().getId()).stream()
                .map(Enrollment::getStudent)
                .filter(student -> !student.isErased())
                .filter(student -> assignment.isFor(student.getId()))
                .toList();
    }

    /** The homework's exceptions by student id, from one query. */
    private Map<Long, AssignmentOverride> overridesOf(Assignment assignment) {
        return overrideRepository.findByAssignmentId(assignment.getId()).stream()
                .collect(Collectors.toMap(o -> o.getStudent().getId(), o -> o));
    }

    /**
     * Progress of each given student on the assignment, from one query for all their attempts at its quiz; each judged
     * against their own deadline when they have one.
     */
    private Map<Long, AssignmentProgress> progressOf(Assignment assignment, List<User> students,
                                                     Map<Long, AssignmentOverride> overrides) {
        if (students.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = students.stream().map(User::getId).toList();
        Map<Long, List<QuizAttempt>> byStudent = attemptRepository
                .findForAssignment(assignment.getQuiz().getId(), ids, assignment.opensAt()).stream()
                .collect(Collectors.groupingBy(a -> a.getStudent().getId()));
        return students.stream().collect(Collectors.toMap(User::getId,
                s -> AssignmentProgress.of(assignment, byStudent.getOrDefault(s.getId(), List.of()),
                        AssignmentOverride.dueAtFor(assignment, overrides.get(s.getId())))));
    }

    /**
     * The summaries of these homeworks (with their quiz, class and catch-up students loaded) from three more queries
     * however many there are - their exceptions, their classes' rosters, the attempts at their tests - not three for
     * each one (audit M1, 2026-11-10: the teacher's Acasă reads every homework).
     */
    private List<AssignmentSummaryDto> summarise(List<Assignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<Long, AssignmentOverride>> overrides = overrideRepository
                .findByAssignmentIdIn(assignments.stream().map(Assignment::getId).toList()).stream()
                .collect(Collectors.groupingBy(o -> o.getAssignment().getId(),
                        Collectors.toMap(o -> o.getStudent().getId(), o -> o)));
        Map<Long, List<User>> rosters = enrollmentRepository
                .findBySchoolClassIdInFetchStudent(assignments.stream().map(a -> a.getSchoolClass().getId())
                        .collect(Collectors.toSet())).stream()
                .map(e -> Map.entry(e.getSchoolClass().getId(), e.getStudent()))
                .filter(e -> !e.getValue().isErased())
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        Set<Long> studentIds = rosters.values().stream().flatMap(List::stream).map(User::getId)
                .collect(Collectors.toSet());
        Map<Long, Map<Long, List<QuizAttempt>>> attempts = studentIds.isEmpty() ? Map.of() : attemptRepository
                .findForAssignments(assignments.stream().map(a -> a.getQuiz().getId()).collect(Collectors.toSet()),
                        studentIds,
                        assignments.stream().map(Assignment::opensAt).min(Comparator.naturalOrder()).orElseThrow())
                .stream()
                .collect(Collectors.groupingBy(a -> a.getQuiz().getId(),
                        Collectors.groupingBy(a -> a.getStudent().getId())));
        Instant now = Instant.now(clock);
        return assignments.stream()
                .map(a -> summarise(a, rosters.getOrDefault(a.getSchoolClass().getId(), List.of()),
                        overrides.getOrDefault(a.getId(), Map.of()),
                        attempts.getOrDefault(a.getQuiz().getId(), Map.of()), now))
                .toList();
    }

    /** One homework's counts from its class roster, its exceptions and its quiz's attempts by student. */
    private static AssignmentSummaryDto summarise(Assignment assignment, List<User> classRoster,
                                                  Map<Long, AssignmentOverride> overrides,
                                                  Map<Long, List<QuizAttempt>> attemptsByStudent, Instant now) {
        // Catch-up homework counts only its chosen students; an excused student is left out of every count.
        List<User> roster = classRoster.stream().filter(s -> assignment.isFor(s.getId())).toList();
        List<User> students = roster.stream()
                .filter(s -> !AssignmentOverride.isExcused(overrides.get(s.getId())))
                .toList();
        int excused = roster.size() - students.size();
        int done = 0, late = 0, inProgress = 0, notStarted = 0;
        for (User student : students) {
            AssignmentProgress p = AssignmentProgress.of(assignment,
                    attemptsByStudent.getOrDefault(student.getId(), List.of()),
                    AssignmentOverride.dueAtFor(assignment, overrides.get(student.getId())));
            if (p.isDone()) {
                done++;
                if (p.late()) {
                    late++;
                }
            } else if (p.state() == State.IN_PROGRESS) {
                inProgress++;
            } else {
                notStarted++;
            }
        }
        return new AssignmentSummaryDto(assignment.getId(), assignment.getQuiz().getId(), assignment.getQuiz().getTitle(),
                assignment.getSchoolClass().getId(), assignment.getSchoolClass().getName(), assignment.getDueAt(),
                assignment.isOverdue(now), students.size(), done, late, inProgress, notStarted, excused,
                assignment.getStudentIds().size(), assignment.isOpen(now) ? null : assignment.getAvailableFrom());
    }
}
