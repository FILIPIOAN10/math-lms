package ro.mathlms.grading;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Every query names the teacher: another teacher's comment is simply not found (404, never 403). */
public interface CommentSnippetRepository extends JpaRepository<CommentSnippet, Long> {

    /** Most used first, then most recently used, then newest - the order the chips are shown in. */
    @Query("""
            select s from CommentSnippet s where s.teacherId = :teacherId
            order by s.useCount desc, s.lastUsedAt desc nulls last, s.id desc
            """)
    List<CommentSnippet> findForTeacher(@Param("teacherId") Long teacherId, Limit limit);

    /** The same text ignoring case - what the unique index (teacher_id, lower(text)) treats as a duplicate. */
    @Query("select s from CommentSnippet s where s.teacherId = :teacherId and lower(s.text) = lower(:text)")
    Optional<CommentSnippet> findSame(@Param("teacherId") Long teacherId, @Param("text") String text);

    long countByTeacherId(Long teacherId);

    /** One more use, atomically; 0 when the comment is not this teacher's (or no longer exists). */
    @Modifying
    @Query("""
            update CommentSnippet s set s.useCount = s.useCount + 1, s.lastUsedAt = :now
            where s.id = :id and s.teacherId = :teacherId
            """)
    int incrementUse(@Param("id") Long id, @Param("teacherId") Long teacherId, @Param("now") Instant now);

    /** 0 when the comment is not this teacher's (or no longer exists). */
    @Modifying
    @Query("delete from CommentSnippet s where s.id = :id and s.teacherId = :teacherId")
    int deleteOwn(@Param("id") Long id, @Param("teacherId") Long teacherId);
}
