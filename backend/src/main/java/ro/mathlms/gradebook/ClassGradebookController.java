package ro.mathlms.gradebook;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.gradebook.ClassGradebookService.Gradebook;
import ro.mathlms.teaching.TeacherScope;

import java.nio.charset.StandardCharsets;

/** The class gradebook as a file the teacher downloads ("Descarcă notele"). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class ClassGradebookController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ClassGradebookService service;
    private final TeacherScope scope;

    public ClassGradebookController(ClassGradebookService service, TeacherScope scope) {
        this.service = service;
        this.scope = scope;
    }

    @GetMapping("/api/admin/classes/{classId}/grades.csv")
    public ResponseEntity<byte[]> csv(@PathVariable Long classId) {
        scope.requireClass(classId);
        Gradebook book = service.forClass(classId);
        // Bytes, not a String: the charset is ours (UTF-8 after the BOM), not whatever a converter would pick.
        byte[] file = ClassGradebookService.toCsv(book).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("note-" + book.className() + ".csv", StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore()) // names and grades: never in a shared or disk cache
                .body(file);
    }
}
