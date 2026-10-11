package ro.mathlms.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewAnswerRepository extends JpaRepository<ReviewAnswer, Long> {

    /** Every review answer of these students, with its question - the replay and the mastery map read them. */
    @Query("select r from ReviewAnswer r join fetch r.item where r.student.id in :studentIds order by r.answeredAt")
    List<ReviewAnswer> findByStudentIdIn(@Param("studentIds") Collection<Long> studentIds);
}
