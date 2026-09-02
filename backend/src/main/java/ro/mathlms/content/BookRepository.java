package ro.mathlms.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findBySchoolClassIdOrderByTitle(Long schoolClassId);

    boolean existsBySchoolClassIdAndTitle(Long schoolClassId, String title);
}
