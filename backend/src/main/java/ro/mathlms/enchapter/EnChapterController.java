package ro.mathlms.enchapter;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.enchapter.EnChapterDtos.Direction;
import ro.mathlms.enchapter.EnChapterDtos.EnChapterPageDto;
import ro.mathlms.enchapter.EnChapterDtos.EnChapterRequest;
import ro.mathlms.enchapter.EnChapterDtos.EnChapterSummaryDto;
import ro.mathlms.enchapter.EnChapterDtos.ExampleDto;
import ro.mathlms.enchapter.EnChapterDtos.ExampleRequest;
import ro.mathlms.enchapter.EnChapterDtos.PhotoDto;

import java.time.Duration;
import java.util.List;

/**
 * The EN chapter pages. Reading ({@code /api/en-chapters}) is open to every active account (see SecurityConfig);
 * changing them ({@code /api/admin/en-chapters}) is the teacher's.
 */
@RestController
public class EnChapterController {

    private final EnChapterService service;

    public EnChapterController(EnChapterService service) {
        this.service = service;
    }

    // --- reading ---

    @GetMapping("/api/en-chapters")
    public List<EnChapterSummaryDto> list() {
        return service.list();
    }

    @GetMapping("/api/en-chapters/{id}")
    public EnChapterPageDto page(@PathVariable Long id) {
        return service.page(id);
    }

    /** A photo never changes (a replaced page is a new photo with a new id), so the browser may keep it a while. */
    @GetMapping("/api/en-chapters/{id}/photos/{photoId}")
    public ResponseEntity<Resource> photo(@PathVariable Long id, @PathVariable Long photoId) {
        return image(service.photo(id, photoId));
    }

    /** The URL carries a version (?v=), so a replaced figure gets a new address. */
    @GetMapping("/api/en-chapters/{id}/examples/{exampleId}/image")
    public ResponseEntity<Resource> exampleImage(@PathVariable Long id, @PathVariable Long exampleId) {
        return image(service.exampleImage(id, exampleId));
    }

    private static ResponseEntity<Resource> image(Resource file) {
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(file);
    }

    // --- the teacher ---

    @PostMapping("/api/admin/en-chapters")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public EnChapterSummaryDto create(@Valid @RequestBody EnChapterRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/admin/en-chapters/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EnChapterSummaryDto update(@PathVariable Long id, @Valid @RequestBody EnChapterRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/api/admin/en-chapters/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/en-chapters/{id}/move")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> move(@PathVariable Long id, @RequestParam Direction direction) {
        service.move(id, direction);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/en-chapters/{id}/photos")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoDto addPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.addPhoto(id, file);
    }

    @DeleteMapping("/api/admin/en-chapters/{id}/photos/{photoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removePhoto(@PathVariable Long id, @PathVariable Long photoId) {
        service.removePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/en-chapters/{id}/examples")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ExampleDto addExample(@PathVariable Long id, @Valid @RequestBody ExampleRequest request) {
        return service.addExample(id, request);
    }

    @PutMapping("/api/admin/en-chapters/{id}/examples/{exampleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ExampleDto updateExample(@PathVariable Long id, @PathVariable Long exampleId,
                                    @Valid @RequestBody ExampleRequest request) {
        return service.updateExample(id, exampleId, request);
    }

    @DeleteMapping("/api/admin/en-chapters/{id}/examples/{exampleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteExample(@PathVariable Long id, @PathVariable Long exampleId) {
        service.deleteExample(id, exampleId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/en-chapters/{id}/examples/{exampleId}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> setExampleImage(@PathVariable Long id, @PathVariable Long exampleId,
                                                @RequestParam("file") MultipartFile file) {
        service.setExampleImage(id, exampleId, file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/en-chapters/{id}/examples/{exampleId}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeExampleImage(@PathVariable Long id, @PathVariable Long exampleId) {
        service.removeExampleImage(id, exampleId);
        return ResponseEntity.noContent().build();
    }
}
