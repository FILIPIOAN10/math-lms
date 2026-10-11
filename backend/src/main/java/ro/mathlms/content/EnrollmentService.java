package ro.mathlms.content;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.Comparator;
import java.util.List;

/** Admin management of who is enrolled in which class (changes are journalled). */
@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final UserRepository userRepository;
    private final AuditLog auditLog;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             SchoolClassRepository schoolClassRepository,
                             UserRepository userRepository, AuditLog auditLog) {
        this.enrollmentRepository = enrollmentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.userRepository = userRepository;
        this.auditLog = auditLog;
    }

    public List<Enrollment> roster(Long classId) {
        requireClass(classId);
        return enrollmentRepository.findBySchoolClassIdFetchStudent(classId);
    }

    /** The classes the logged-in student attends, ordered by name — what the student may browse. */
    @Transactional(readOnly = true)
    public List<SchoolClass> myClasses(String studentEmail) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ContentNotFoundException("No account for " + studentEmail));
        return enrollmentRepository.findByStudentIdFetchClass(student.getId()).stream()
                .map(Enrollment::getSchoolClass)
                .sorted(Comparator.comparing(SchoolClass::getName, NaturalOrder.BY_NAME))
                .toList();
    }

    /** The classes attended by the children linked to this parent — what the parent may browse. */
    @Transactional(readOnly = true)
    public List<SchoolClass> classesOfMyChildren(String parentEmail) {
        User parent = userRepository.findByEmail(parentEmail)
                .orElseThrow(() -> new ContentNotFoundException("No account for " + parentEmail));
        return enrollmentRepository.findClassesOfChildrenOf(parent.getId()).stream()
                .sorted(Comparator.comparing(SchoolClass::getName, NaturalOrder.BY_NAME))
                .toList();
    }

    @Transactional
    public Enrollment enroll(Long classId, Long studentId) {
        SchoolClass schoolClass = requireClass(classId);
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ContentNotFoundException("Student", studentId));
        if (enrollmentRepository.existsByStudentIdAndSchoolClassId(studentId, classId)) {
            throw new DuplicateContentException("This student is already enrolled in this class");
        }
        // The Enrollment constructor enforces that the account's real role is STUDENT.
        Enrollment saved = enrollmentRepository.save(new Enrollment(student, schoolClass));
        auditLog.record(AuditEvent.of(AuditAction.STUDENT_ENROLLED, schoolClass.getId(), schoolClass.getName())
                .about(student));
        return saved;
    }

    @Transactional
    public void unenroll(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ContentNotFoundException("Enrollment", enrollmentId));
        SchoolClass schoolClass = enrollment.getSchoolClass();
        enrollmentRepository.deleteById(enrollmentId);
        auditLog.record(AuditEvent.of(AuditAction.STUDENT_UNENROLLED, schoolClass.getId(), schoolClass.getName())
                .about(enrollment.getStudent()));
    }

    private SchoolClass requireClass(Long classId) {
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> new ContentNotFoundException("SchoolClass", classId));
    }
}
