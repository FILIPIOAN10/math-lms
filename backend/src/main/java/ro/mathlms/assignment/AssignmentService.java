package ro.mathlms.assignment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.assignment.AssignmentDtos.AssignmentSummaryDto;
import ro.mathlms.assignment.AssignmentDtos.StudentAssignmentDto;
import ro.mathlms.assignment.AssignmentDtos.StudentStatusDto;
import ro.mathlms.assignment.AssignmentProgress.State;
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
    private final QuizRepository quizRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final OutboxEventPublisher publisher;
    private final Clock clock;
    private final int reminderLeadHours;

    public AssignmentService(AssignmentRepository assignmentRepository, QuizRepository quizRepository,
                             SchoolClassRepository schoolClassRepository, EnrollmentRepository enrollmentRepository,
                             QuizAttemptRepository attemptRepository, UserRepository userRepository,
                             OutboxEventPublisher publisher, Clock clock,
                             @Value("${app.assignments.reminder-lead-hours:24}") int reminderLeadHours) {
        this.assignmentRepository = assignmentRepository;
        this.quizRepository = quizRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
        this.publisher = publisher;
        this.clock = clock;
        this.reminderLeadHours = reminderLeadHours;
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
            throw new InvalidAssignmentException("Doar un quiz publicat poate fi dat ca temă");
        }
        if (quiz.getSchoolClass() != null && !quiz.getSchoolClass().getId().equals(schoolClassId)) {
            throw new InvalidAssignmentException("Quiz-ul este destinat altă clasă decât cea aleasă");
        }
        requireFuture(dueAt);
        if (assignmentRepository.existsByQuizIdAndSchoolClassId(quizId, schoolClassId)) {
            throw new AssignmentConflictException("Quiz-ul este deja dat ca temă acestei clase — modifică termenul");
        }
        return assignmentRepository.save(new Assignment(quiz, schoolClass, dueAt, Instant.now(clock)));
    }

    @Transactional
    public Assignment reschedule(Long id, Instant dueAt) {
        Assignment assignment = require(id);
        requireFuture(dueAt);
        assignment.reschedule(dueAt);
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public void delete(Long id) {
        assignmentRepository.delete(require(id));
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
        Map<Long, AssignmentProgress> progress = progressOf(assignment, students);
        return students.stream().map(student -> {
            AssignmentProgress p = progress.get(student.getId());
            return new StudentStatusDto(student.getId(), student.getFullName(), p.state(), p.submittedAt(), p.late(),
                    p.score(), p.attemptId());
        }).toList();
    }

    // ---------------------------------------------------------------- student: my homework

    /** The homework of the student's classes: not yet handed in first (soonest deadline first), then the finished ones. */
    @Transactional(readOnly = true)
    public List<StudentAssignmentDto> myAssignments(String studentEmail) {
        User student = userRepository.findByEmail(studentEmail).orElse(null);
        if (student == null || student.getRole() != Role.STUDENT) {
            return List.of();
        }
        List<Long> classIds = enrollmentRepository.findClassIdsByStudentId(student.getId());
        if (classIds.isEmpty()) {
            return List.of();
        }
        List<Assignment> assignments = assignmentRepository.findBySchoolClassIdsFetched(classIds);
        List<Long> quizIds = assignments.stream().map(a -> a.getQuiz().getId()).distinct().toList();
        Map<Long, List<QuizAttempt>> attemptsByQuiz = attemptRepository.findByStudentIdAndQuizIdIn(student.getId(), quizIds)
                .stream().collect(Collectors.groupingBy(a -> a.getQuiz().getId()));
        Instant now = Instant.now(clock);

        return assignments.stream()
                .map(a -> {
                    AssignmentProgress p = AssignmentProgress.of(a, attemptsByQuiz.getOrDefault(a.getQuiz().getId(), List.of()));
                    return new Row(a, p, new StudentAssignmentDto(a.getId(), a.getQuiz().getId(), a.getQuiz().getTitle(),
                            a.getSchoolClass().getName(), a.getDueAt(), p.state(), p.late(),
                            !p.isDone() && a.isOverdue(now), p.attemptId(), a.getQuiz().getTimeLimitMinutes()));
                })
                .sorted(Comparator.<Row, Boolean>comparing(r -> r.progress().isDone())
                        .thenComparing(r -> r.assignment().getDueAt()))
                .map(Row::dto)
                .toList();
    }

    private record Row(Assignment assignment, AssignmentProgress progress, StudentAssignmentDto dto) {
    }

    // ---------------------------------------------------------------- reminders

    /** Assignments whose deadline is within the lead time and whose reminder has not been queued yet. */
    @Transactional(readOnly = true)
    public List<Long> findDueForReminderIds() {
        Instant now = Instant.now(clock);
        return assignmentRepository.findIdsDueForReminder(now, now.plus(Duration.ofHours(reminderLeadHours)));
    }

    /**
     * Queues one reminder per student who has not handed in, inside this transaction (so the outbox rows commit with the
     * "reminder sent" mark). Re-checked under the row lock: a second run, another instance, an assignment moved out of the
     * window or one already reminded is a no-op. An assignment with nobody pending is still marked, so it is not retried.
     */
    @Transactional
    public void queueReminders(Long id) {
        Assignment assignment = assignmentRepository.findByIdForUpdate(id).orElse(null);
        Instant now = Instant.now(clock);
        if (assignment == null || assignment.getReminderSentAt() != null
                || !assignment.getDueAt().isAfter(now)
                || assignment.getDueAt().isAfter(now.plus(Duration.ofHours(reminderLeadHours)))) {
            return;
        }
        List<User> students = rosterOf(assignment);
        Map<Long, AssignmentProgress> progress = progressOf(assignment, students);
        for (User student : students) {
            if (!progress.get(student.getId()).isDone()) {
                publisher.publish(OutboxEventTypes.ASSIGNMENT_REMINDER_EMAIL,
                        new AssignmentReminderPayload(assignment.getId(), student.getId()));
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

    /** Progress of each given student on the assignment, from one query for all their attempts at its quiz. */
    private Map<Long, AssignmentProgress> progressOf(Assignment assignment, List<User> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = students.stream().map(User::getId).toList();
        Map<Long, List<QuizAttempt>> byStudent = attemptRepository
                .findForAssignment(assignment.getQuiz().getId(), ids, assignment.getCreatedAt()).stream()
                .collect(Collectors.groupingBy(a -> a.getStudent().getId()));
        return students.stream().collect(Collectors.toMap(User::getId,
                s -> AssignmentProgress.of(assignment, byStudent.getOrDefault(s.getId(), List.of()))));
    }

    private AssignmentSummaryDto summarise(Assignment assignment) {
        List<User> students = rosterOf(assignment);
        Map<Long, AssignmentProgress> progress = progressOf(assignment, students);
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
                assignment.isOverdue(Instant.now(clock)), students.size(), done, late, inProgress, notStarted);
    }
}
