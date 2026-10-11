package ro.mathlms.student;

/** No student account has this id (a parent's or the teacher's id counts as "no student"). */
public class StudentNotFoundException extends RuntimeException {
    public StudentNotFoundException(Long id) {
        super("Nu există elevul " + id + ".");
    }
}
