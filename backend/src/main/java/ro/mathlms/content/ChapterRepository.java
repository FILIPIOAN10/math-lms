package ro.mathlms.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByBookIdOrderByTitle(Long bookId);

    boolean existsByBookIdAndTitle(Long bookId, String title);
}
