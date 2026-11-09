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
        if (assignmentRepository.existsByQuizIdAndSchoolClassId(quizId, schoolClassId)) {
            throw new AssignmentConflictException("Testul este deja dat ca temă acestei clase — modifică termenul");
        }
        Assignment saved = assignmentRepository.save(new Assignment(quiz, schoolClass, dueAt, Instant.now(clock)));
        auditLog.record(AuditEvent.of(AuditAction.HOMEWORK_ASSIGNED, saved.getId(), label(saved))
                .details("termen " + AuditText.dateTime(dueAt)));
        return saved;
    }

    /**
     * The same homework for several classes in one go (Canvas: one assignment, several sections), each class its own
     * {@link Assignment} with the same deadline. All or nothing: one refused class (already given, another class's quiz)
     * leaves none of them created. A class named twice counts once.
     */
    @Transactional
    public List<Assignment> createForClasses(Long quizId, List<Long> schoolClassIds, Instant dueAt) {
        List<Long> ids = schoolClassIds == null ? List.of() : schoolClassIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            throw new InvalidAssignmentException("Alege cel puțin o clasă");
        }
        return ids.stream().map(classId -> create(quizId, classId, dueAt)).toList();
    }

    @Transactional
    public Assignment reschedule(Long id, Instant dueAt) {
        Assignment assignment = require(id);
        requireFuture(dueAt);
        Instant before = assignment.getDueAt();
        assignment.reschedule(dueAt);
        Assignment saved = assignmentRepository.save(assignment);
        if (!before.equals(dueAt)) {
            auditLog.record(AuditEvent.of(AuditAction.HOMEWORK_RESCHEDULED, id, label(assignment))
                    .details("termen " + AuditText.dateTime(before) + " → " + AuditText.dateTime(dueAt)));
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

    @Transactional(readOnly = true)
    public List<AssignmentSummaryDto> list() {
        return assignmentRepository.findAllFetched().stream().map(this::summarise).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentSummaryDto summaryOf(Long id) {
        return summarise(assignmentRepository.findByIdFetched(id)
                .orElseThrow(() -> new AssignmentNotFoundException("Nu există tema " + id)));
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

    /** The same; {@code includeExcused} keeps excused homework too (flagged) - the teacher's page for one student. */
    @Transactional(readOnly = true)
    public List<StudentAssignmentDto> myAssignments(String studentEmail, boolean includeExcused) {
        User student = userRepository.findByEmail(studentEmail).orElse(null);
        if (student == null || student.getRole() != Role.STUDENT) {
            return List.of();
        }
        List<Long> classIds = enrollmentRepository.findClassIdsByStudentId(student.getId());
        if (classIds.isEmpty()) {
            return List.of();
        }
        // An unpublished quiz cannot be started, so its homework is hidden until it is published again.
        List<Assignment> assignments = assignmentRepository.findBySchoolClassIdsFetched(classIds).stream()
                .filter(a -> a.getQuiz().getStatus() == QuizStatus.PUBLISHED)
                .toList();
        List<Long> quizIds = assignments.stream().map(a -> a.getQuiz().getId()).distinct().toList();
        Map<Long, List<QuizAttempt>> attemptsByQuiz = attemptRepository.findByStudentIdAndQuizIdIn(student.getId(), quizIds)
                .stream().collect(Collectors.groupingBy(a -> a.getQuiz().getId()));
        Instant now = Instant.now(clock);
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
                            excused));
                })
                .filter(r -> includeExcused || !r.dto().excused() || r.progress().isDone())
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
                || !assignment.getDueAt().isAfter(now)
                || assignment.getDueAt().isAfter(now.plus(Duration.ofHours(reminderLeadHours)))) {
            return;
        }
        List<User> students = rosterOf(assignment);
        // A student with an exception is not reminded of the class deadline: excused, or working to a date of their own.
        Map<Long, AssignmentOverride> overrides = overridesOf(assignment);
        Map<Long, AssignmentProgress> progress = progressOf(assignment, students, overrides);
        for (User student : students) {
            if (!overrides.containsKey(student.getId()) && !progress.get(student.getId()).isDone()) {
                publisher.publish(OutboxEventTypes.ASSIGNMENT_REMINDER_EMAIL,
                        new AssignmentReminderPayload(assignment.getId(), student.getId()));
                if (remindParents && student.getParent() != null && !student.getParent().isErased()) {
                    publisher.publish(OutboxEventTypes.ASSIGNMENT_REMINDER_EMAIL, new AssignmentReminderPayload(
                            assignment.getId(), student.getId(), AssignmentReminderPayload.Recipient.PARENT));
                }
            }
        }
        assignment.markReminderSent(now);
        assignmentRepository.save(assignment);
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

    /** The class roster as users, erased (anonymised) accounts left out. */
    private List<User> rosterOf(Assignment assignment) {
        return enrollmentRepository.findBySchoolClassIdFetchStudent(assignment.getSchoolClass().getId()).stream()
                .map(Enrollment::getStudent)
                .filter(student -> !student.isErased())
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
                .findForAssignment(assignment.getQuiz().getId(), ids, assignment.getCreatedAt()).stream()
                .collect(Collectors.groupingBy(a -> a.getStudent().getId()));
        return students.stream().collect(Collectors.toMap(User::getId,
                s -> AssignmentProgress.of(assignment, byStudent.getOrDefault(s.getId(), List.of()),
                        AssignmentOverride.dueAtFor(assignment, overrides.get(s.getId())))));
    }

    private AssignmentSummaryDto summarise(Assignment assignment) {
        Map<Long, AssignmentOverride> overrides = overridesOf(assignment);
        // An excused student is left out of every count: the homework does not count for them.
        List<User> roster = rosterOf(assignment);
        List<User> students = roster.stream()
                .filter(s -> !AssignmentOverride.isExcused(overrides.get(s.getId())))
                .toList();
        int excused = roster.size() - students.size();
        Map<Long, AssignmentProgress> progress = progressOf(assignment, students, overrides);
        int done = 0, late = 0, inProgress = 0, notStarted = 0;
        for (AssignmentProgress p : progress.values()) {
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
                assignment.isOverdue(Instant.now(clock)), students.size(), done, late, inProgress, notStarted, excused);
    }
}
