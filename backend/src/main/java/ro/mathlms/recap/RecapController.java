package ro.mathlms.recap;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.recap.RecapDtos.CatchUpDto;
import ro.mathlms.recap.RecapDtos.CatchUpRequest;
import ro.mathlms.recap.RecapDtos.ChapterCountDto;
import ro.mathlms.recap.RecapDtos.CreateQuizRequest;
import ro.mathlms.recap.RecapDtos.CreatedQuizDto;
import ro.mathlms.recap.RecapDtos.ImportResult;
import ro.mathlms.recap.RecapDtos.RecapExerciseDto;
import ro.mathlms.recap.RecapDtos.ReviewRequest;
import ro.mathlms.recap.RecapDtos.UpdateRequest;
import ro.mathlms.storage.StoredImage;

import java.util.List;

/**
 * „Culegere” - the „Teme recapitulative” collection. Teachers read, correct and approve it (it is shared, like the EN
 * pages) and make tests and catch-up homework from it; only an admin loads the book's zip.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class RecapController {

    private final RecapService service;
    private final RecapImportService importService;

    public RecapController(RecapService service, RecapImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    @PostMapping("/api/admin/recap/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult load(@RequestParam("file") MultipartFile file) {
        return importService.load(file);
    }

    @GetMapping("/api/admin/recap/exercises")
    public List<RecapExerciseDto> list(@RequestParam(required = false) RecapReview review,
                                       @RequestParam(required = false) Long enChapterId,
                                       @RequestParam(required = false) Integer tema,
                                       @RequestParam(required = false) Boolean verified,
                                       @RequestParam(required = false) String q) {
        return service.list(review, enChapterId, tema, verified, q);
    }

    @GetMapping("/api/admin/recap/exercises/{id}")
    public RecapExerciseDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/api/admin/recap/chapters")
    public List<ChapterCountDto> chapters() {
        return service.approvedByChapter();
    }

    @PutMapping("/api/admin/recap/exercises/{id}")
    public RecapExerciseDto update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/api/admin/recap/exercises/{id}/review")
    public RecapExerciseDto review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return service.review(id, request.review());
    }

    @PostMapping("/api/admin/recap/exercises/{id}/no-figure")
    public RecapExerciseDto waiveFigure(@PathVariable Long id) {
        return service.waiveFigure(id);
    }

    @PostMapping("/api/admin/recap/exercises/{id}/image")
    public RecapExerciseDto attachImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.attachImage(id, file);
    }

    @GetMapping("/api/admin/recap/exercises/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        Resource image = service.image(id);
        return StoredImage.ok(image).cacheControl(CacheControl.noCache().cachePrivate()).body(image);
    }

    @PostMapping("/api/admin/recap/quizzes")
    public ResponseEntity<CreatedQuizDto> createQuiz(@Valid @RequestBody CreateQuizRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedQuizDto(service.createQuiz(request)));
    }

    @PostMapping("/api/admin/recap/catch-up")
    public ResponseEntity<CatchUpDto> catchUp(@Valid @RequestBody CatchUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.catchUp(request));
    }
}
