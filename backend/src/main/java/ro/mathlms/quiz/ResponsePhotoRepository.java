package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResponsePhotoRepository extends JpaRepository<ResponsePhoto, Long> {

    /** Storage keys of every rezolvare photo a student uploaded — used by GDPR erasure to delete the files. */
    @Query("select p.imageKey from ResponsePhoto p where p.response.attempt.student.id = :userId")
    List<String> findImageKeysByStudentId(@Param("userId") Long userId);

    /** GDPR erasure: the photo rows go with their files; the (anonymised) answers and their points stay. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ResponsePhoto p where p.response.id in "
            + "(select r.id from ItemResponse r where r.attempt.student.id = :userId)")
    void deleteByStudentId(@Param("userId") Long userId);
}
