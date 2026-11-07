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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.quiz.QuizDtos.ItemDto;
import ro.mathlms.quiz.QuizDtos.QuizDetailDto;
import ro.mathlms.quiz.QuizDtos.QuizSummaryDto;
import ro.mathlms.teaching.TeacherScope;

import java.util.List;

/** Admin quiz-builder API. All routes are under {@code /api/admin/...} (ADMIN). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class QuizAdminController {

    private final QuizAdminService service;
    private final TeacherScope scope;
    private final AnswerKeyChecks answerKeyChecks;

    public QuizAdminController(QuizAdminService service, TeacherScope scope, AnswerKeyChecks answerKeyChecks) {
        this.service = service;
        this.scope = scope;
        this.answerKeyChecks = answerKeyChecks;
    }

    @GetMapping("/api/admin/quizzes")
    public List<QuizSummaryDto> list() {
        return scope.keepOwnedQuizzes(service.listQuizzes(), Quiz::getId).stream().map(QuizSummaryDto::from).toList();
    }

    /**
     * Questions, papers, mean grade, deadline and answers to check per quiz, for the list's rows and Acasă (a TEACHER
     * gets their own tests). The answer check reads statistics, so it runs only on the tests the caller may see.
     */
    @GetMapping("/api/admin/quizzes/summary")
    public List<QuizListSummaryDto> summaries() {
        return answerKeyChecks.withToCheck(scope.keepOwnedQuizzes(service.listSummaries(), QuizListSummaryDto::quizId));
    }

    @GetMapping("/api/admin/quizzes/{id}")
    public QuizDetailDto detail(@PathVariable Long id) {
        scope.requireQuiz(id);
        return service.getQuizDetail(id);
    }

    @PostMapping("/api/admin/quizzes")
    public ResponseEntity<QuizSummaryDto> create(@Valid @RequestBody QuizRequest request) {
        Quiz created = service.createQuiz(request.title(), request.description(), request.schoolClassId(),
                request.timeLimitMinutes(), Boolean.TRUE.equals(request.practiceAllowed()), request.chapterId(),
                request.maxAttempts(), request.publisherId(), request.bookId());
        return ResponseEntity.status(HttpStatus.CREATED).body(QuizSummaryDto.from(created));
    }

    @PutMapping("/api/admin/quizzes/{id}")
    public QuizSummaryDto update(@PathVariable Long id, @Valid @RequestBody QuizRequest request) {
        scope.requireQuiz(id);
        return QuizSummaryDto.from(
                service.updateQuiz(id, request.title(), request.description(), request.schoolClassId(),
                        request.timeLimitMinutes(), Boolean.TRUE.equals(request.practiceAllowed()), request.chapterId(),
                        request.maxAttempts(), request.publisherId(), request.bookId()));
    }

    @PostMapping("/api/admin/quizzes/{id}/copy")
    public ResponseEntity<QuizSummaryDto> copy(@PathVariable Long id) {
        scope.requireQuiz(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(QuizSummaryDto.from(service.copyQuiz(id)));
    }

    /** T2: a draft quiz from the chapter's exercises the teacher ticked; the editor opens on it next. */
    @PostMapping("/api/admin/chapters/{chapterId}/quiz")
    public ResponseEntity<QuizSummaryDto> createFromChapter(@PathVariable Long chapterId,
                                                            @Valid @RequestBody ChapterQuizRequest request) {
        scope.requireChapter(chapterId);
        Quiz created = service.createFromExercises(chapterId, request.title(), request.exerciseIds(), request.points());
        return ResponseEntity.status(HttpStatus.CREATED).body(QuizSummaryDto.from(created));
    }

    @PostMapping("/api/admin/quizzes/{id}/publish")
    public QuizSummaryDto publish(@PathVariable Long id) {
        scope.requireQuiz(id);
        return QuizSummaryDto.from(service.setPublished(id, true));
    }

    /** Who is writing the test now and the classes it is homework for: asked just before unpublishing it. */
    @GetMapping("/api/admin/quizzes/{id}/activity")
    public QuizDtos.QuizActivityDto activity(@PathVariable Long id) {
        scope.requireQuiz(id);
        return service.getActivity(id);
    }

    @PostMapping("/api/admin/quizzes/{id}/unpublish")
    public QuizSummaryDto unpublish(@PathVariable Long id) {
        scope.requireQuiz(id);
        return QuizSummaryDto.from(service.setPublished(id, false));
    }

    @DeleteMapping("/api/admin/quizzes/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scope.requireQuiz(id);
        service.deleteQuiz(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/quizzes/{quizId}/items")
    public ResponseEntity<ItemDto> addItem(@PathVariable Long quizId, @Valid @RequestBody ItemRequest request) {
        scope.requireQuiz(quizId);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addItem(quizId, request));
    }

    @PutMapping("/api/admin/quiz-items/{itemId}")
    public ItemDto updateItem(@PathVariable Long itemId, @Valid @RequestBody ItemRequest request) {
        scope.requireQuizItem(itemId);
        return service.updateItem(itemId, request);
    }

    /** Applies the EN chapters the teacher kept (stage 3: proposals from "Sugerează capitolele (AI)"). */
    @PutMapping("/api/admin/quizzes/{id}/en-chapters")
    public ResponseEntity<Void> assignEnChapters(@PathVariable Long id, @Valid @RequestBody EnChapterAssignments request) {
        scope.requireQuiz(id);
        service.assignEnChapters(id, request.items());
        return ResponseEntity.noContent().build();
    }

    /** One place up or down, inside the question's section. */
    @PostMapping("/api/admin/quiz-items/{itemId}/move")
    public ResponseEntity<Void> moveItem(@PathVariable Long itemId, @RequestParam boolean up) {
        scope.requireQuizItem(itemId);
        service.moveItem(itemId, up);
        return ResponseEntity.noContent().build();
    }

    /** A copy of the question, right under it. */
    @PostMapping("/api/admin/quiz-items/{itemId}/copy")
    public ResponseEntity<Void> duplicateItem(@PathVariable Long itemId) {
        scope.requireQuizItem(itemId);
        service.duplicateItem(itemId);
        return ResponseEntity.noContent().build();
    }

    /** What a wrong option gives away, in the teacher's words; allowed on a test already taken. */
    @PutMapping("/api/admin/quiz-options/{optionId}/mistake")
    public ResponseEntity<Void> explainMistake(@PathVariable Long optionId, @Valid @RequestBody MistakeRequest request) {
        scope.requireQuizItem(service.itemIdOfOption(optionId));
        service.explainMistake(optionId, request.mistake());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/quiz-items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long itemId) {
        scope.requireQuizItem(itemId);
        service.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }
}
