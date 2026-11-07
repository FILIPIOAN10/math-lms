package ro.mathlms.publisher;

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
import ro.mathlms.publisher.PublisherDtos.PublisherDto;
import ro.mathlms.publisher.PublisherDtos.PublisherRequest;

import java.time.Duration;
import java.util.List;

/**
 * "Edituri": teachers read the list (the test's "Editură" dropdown); only administrators add, change or delete a
 * publisher and its logo.
 */
@RestController
public class PublisherController {

    private final PublisherService service;

    public PublisherController(PublisherService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/publishers")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<PublisherDto> list() {
        return service.list();
    }

    @PostMapping("/api/admin/publishers")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PublisherDto create(@RequestBody PublisherRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/admin/publishers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PublisherDto update(@PathVariable Long id, @RequestBody PublisherRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/api/admin/publishers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/publishers/{id}/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> setLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        service.setLogo(id, file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/publishers/{id}/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeLogo(@PathVariable Long id) {
        service.removeLogo(id);
        return ResponseEntity.noContent().build();
    }

    /** The URL carries ?v=logoVersion, so a replaced logo gets a new address and each version may be kept a while. */
    @GetMapping("/api/admin/publishers/{id}/logo")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Resource> logo(@PathVariable Long id) {
        Resource file = service.loadLogo(id);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(file);
    }
}
