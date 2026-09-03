package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemResponseRepository extends JpaRepository<ItemResponse, Long> {

    List<ItemResponse> findByAttemptId(Long attemptId);

    Optional<ItemResponse> findByAttemptIdAndItemId(Long attemptId, Long itemId);
}
