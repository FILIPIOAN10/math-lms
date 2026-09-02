package ro.mathlms.quiz;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.quiz.QuizDtos.ItemDto;
import ro.mathlms.quiz.QuizDtos.QuizDetailDto;
import ro.mathlms.quiz.QuizDtos.QuizSummaryDto;

import java.util.List;

/** Admin quiz-builder API. All routes are under {@code /api/admin/...} (ADMIN). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class QuizAdminController {

    private final QuizAdminService service;

    public QuizAdminController(QuizAdminService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/quizzes")
    public List<QuizSummaryDto> list() {
        return service.listQuizzes().stream().map(QuizSummaryDto::from).toList();
    }

    @GetMapping("/api/admin/quizzes/{id}")
    public QuizDetailDto detail(@PathVariable Long id) {
        return service.getQuizDetail(id);
    }

    @PostMapping("/api/admin/quizzes")
    public ResponseEntity<QuizSummaryDto> create(@Valid @RequestBody QuizRequest request) {
        Quiz created = service.createQuiz(request.title(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(QuizSummaryDto.from(created));
    }

    @PutMapping("/api/admin/quizzes/{id}")
    public QuizSummaryDto update(@PathVariable Long id, @Valid @RequestBody QuizRequest request) {
        return QuizSummaryDto.from(service.updateQuiz(id, request.title(), request.description()));
    }

    @PostMapping("/api/admin/quizzes/{id}/publish")
    public QuizSummaryDto publish(@PathVariable Long id) {
        return QuizSummaryDto.from(service.setPublished(id, true));
    }

    @PostMapping("/api/admin/quizzes/{id}/unpublish")
    public QuizSummaryDto unpublish(@PathVariable Long id) {
        return QuizSummaryDto.from(service.setPublished(id, false));
    }

    @DeleteMapping("/api/admin/quizzes/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteQuiz(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/quizzes/{quizId}/items")
    public ResponseEntity<ItemDto> addItem(@PathVariable Long quizId, @Valid @RequestBody ItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addItem(quizId, request));
    }

    @PutMapping("/api/admin/quiz-items/{itemId}")
    public ItemDto updateItem(@PathVariable Long itemId, @Valid @RequestBody ItemRequest request) {
        return service.updateItem(itemId, request);
    }

    @DeleteMapping("/api/admin/quiz-items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long itemId) {
        service.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }
}
