package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemResponseRepository extends JpaRepository<ItemResponse, Long> {

    List<ItemResponse> findByAttemptId(Long attemptId);

    Optional<ItemResponse> findByAttemptIdAndItemId(Long attemptId, Long itemId);

    /** Storage keys of every rezolvare photo a student uploaded — used by GDPR erasure to delete the files. */
    @Query("select r.imageKey from ItemResponse r"
            + " where r.attempt.student.id = :userId and r.imageKey is not null")
    List<String> findImageKeysByStudentId(@Param("userId") Long userId);

    /** GDPR erasure: detach a student's photos from their (retained, anonymised) responses. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ItemResponse r set r.imageKey = null where r.attempt.student.id = :userId")
    void clearPhotosByStudentId(@Param("userId") Long userId);
}
