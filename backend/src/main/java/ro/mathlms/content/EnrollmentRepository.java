package ro.mathlms.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findBySchoolClassId(Long schoolClassId);

    List<Enrollment> findByStudentId(Long studentId);

    boolean existsByStudentIdAndSchoolClassId(Long studentId, Long schoolClassId);
}
