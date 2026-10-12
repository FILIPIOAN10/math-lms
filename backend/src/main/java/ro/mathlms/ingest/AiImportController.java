package ro.mathlms.ingest;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.ingest.AiImportDtos.AiImportDto;
import ro.mathlms.ingest.AiImportDtos.AiImportSettingsDto;
import ro.mathlms.ingest.AiImportDtos.CreateQuizFromImportRequest;
import ro.mathlms.ingest.AiImportDtos.CreatedQuizDto;

import java.util.List;

/** Faza 3 - a quiz from textbook photos. Teachers only; each sees only their own imports. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AiImportController {

    private final AiImportService service;

    public AiImportController(AiImportService service) {
        this.service = service;
    }

    /** Always answers (the UI asks it to decide whether to show the button). */
    @GetMapping("/api/admin/ai-imports/settings")
    public AiImportSettingsDto settings(Authentication auth) {
        return service.settings(auth.getName());
    }

    /** 202: stored and queued; poll the import until it is DONE or FAILED. */
    @PostMapping("/api/admin/ai-imports")
    public ResponseEntity<AiImportDto> create(@RequestParam("files") List<MultipartFile> files,
                                              @RequestParam(required = false) Long schoolClassId,
                                              @RequestParam(required = false) Long chapterId,
                                              Authentication auth) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.create(auth.getName(), files, schoolClassId, chapterId));
    }

    @GetMapping("/api/admin/ai-imports")
    public List<AiImportDto> list(Authentication auth) {
        return service.list(auth.getName());
    }

    @GetMapping("/api/admin/ai-imports/{id}")
    public AiImportDto get(@PathVariable Long id, Authentication auth) {
        return service.get(auth.getName(), id);
    }

    @GetMapping("/api/admin/ai-imports/{id}/pages/{number}")
    public ResponseEntity<Resource> page(@PathVariable Long id, @PathVariable int number, Authentication auth) {
        Resource file = service.page(auth.getName(), id, number);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(file).orElse(MediaType.IMAGE_JPEG))
                .cacheControl(CacheControl.noStore())
                .body(file);
    }

    @PostMapping("/api/admin/ai-imports/{id}/retry")
    public AiImportDto retry(@PathVariable Long id, Authentication auth) {
        return service.retry(auth.getName(), id);
    }

    @DeleteMapping("/api/admin/ai-imports/{id}")
    public ResponseEntity<Void> discard(@PathVariable Long id, Authentication auth) {
        service.discard(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/ai-imports/{id}/quiz")
    public ResponseEntity<CreatedQuizDto> createQuiz(@PathVariable Long id,
                                                     @Valid @RequestBody CreateQuizFromImportRequest request,
                                                     Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedQuizDto(service.createQuiz(auth.getName(), id, request)));
    }
}
