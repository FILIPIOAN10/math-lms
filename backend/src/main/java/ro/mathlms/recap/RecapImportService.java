package ro.mathlms.recap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.common.AfterTransaction;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.quiz.EnChapterDetector;
import ro.mathlms.quiz.NumericAnswer;
import ro.mathlms.recap.RecapDtos.ImportResult;
import ro.mathlms.storage.FileService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Loads the zip made by {@code tutor/scripts/teme_bundle.py}: {@code culegere.json} plus the drawings cut from the book.
 * Only keys not seen before are added, so the same zip (or a bigger one later) can be loaded again without touching what
 * teachers already corrected or approved. Every new exercise starts as a draft. The book's text never comes from the
 * repository - only from this upload.
 */
@Service
public class RecapImportService {

    private static final Logger log = LoggerFactory.getLogger(RecapImportService.class);

    /** Bounds on what is read out of the zip, whatever its size on the wire says. */
    static final long MAX_JSON_BYTES = 20L * 1024 * 1024;
    static final long MAX_IMAGE_BYTES = 4L * 1024 * 1024;
    static final int MAX_ENTRIES = 3000;
    /** All of it together (audit 2026-11-27 L2): 3000 entries of 4 MB would otherwise all sit in memory at once. */
    static final long MAX_TOTAL_BYTES = 300L * 1024 * 1024;

    private final RecapExerciseRepository repository;
    private final EnChapterRepository enChapterRepository;
    private final FileService fileService;
    private final ObjectMapper objectMapper;
    private final String imagesDir;

    public RecapImportService(RecapExerciseRepository repository, EnChapterRepository enChapterRepository,
                              FileService fileService, ObjectMapper objectMapper,
                              @Value("${app.storage.question-images-dir}") String imagesDir) {
        this.repository = repository;
        this.enChapterRepository = enChapterRepository;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
        this.imagesDir = imagesDir;
    }

    @Transactional
    public ImportResult load(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRecapException("Alege fișierul .zip al culegerii.");
        }
        Map<String, byte[]> entries = unzip(file, MAX_TOTAL_BYTES);
        byte[] json = entries.get("culegere.json");
        if (json == null) {
            throw new InvalidRecapException("Arhiva nu conține culegere.json (fă-o cu scripts/teme_bundle.py).");
        }
        JsonNode exercises;
        try {
            exercises = objectMapper.readTree(json).path("exercises");
        } catch (JacksonException e) {
            throw new InvalidRecapException("culegere.json nu poate fi citit.");
        }
        if (!exercises.isArray()) {
            throw new InvalidRecapException("culegere.json nu are lista „exercises”.");
        }
        Set<String> known = repository.findAllKeys();
        int added = 0;
        int skipped = 0;
        int figuresAdded = 0;
        for (JsonNode row : exercises) {
            String key = text(row, "key");
            if (key == null || key.length() > 40) {
                throw new InvalidRecapException("Un exercițiu din culegere.json nu are cheie validă.");
            }
            if (!known.add(key)) {
                skipped++;
                if (addFigure(key, text(row, "figure"), entries)) {
                    figuresAdded++;
                }
                continue;
            }
            repository.save(build(key, row, entries));
            added++;
        }
        log.info("Recap import: {} added, {} already there, {} drawings added to them", added, skipped, figuresAdded);
        return new ImportResult(added, skipped, added + skipped, figuresAdded);
    }

    /**
     * An exercise already loaded gets the zip's drawing only when it has none yet (found since, e.g. by the figure number
     * its text cites): its text, review and a picture a teacher chose are never touched.
     */
    private boolean addFigure(String key, String figure, Map<String, byte[]> entries) {
        if (figure == null || !entries.containsKey(figure)) {
            return false;
        }
        RecapExercise exercise = repository.findBySourceKey(key).orElse(null);
        if (exercise == null || exercise.getImageKey() != null) {
            return false;
        }
        exercise.showImage(store(figure, entries.get(figure)));
        repository.save(exercise);
        return true;
    }

    private RecapExercise build(String key, JsonNode row, Map<String, byte[]> entries) {
        String statement = text(row, "statement");
        if (statement == null) {
            throw new InvalidRecapException("Exercițiul " + key + " nu are enunț.");
        }
        String status = text(row, "aiStatus");
        RecapExercise exercise = new RecapExercise(key, row.path("tema").asInt(0), row.path("number").asInt(0),
                status == null || status.length() > 20 ? "UNPARSED" : status);
        List<RecapPart> parts = new ArrayList<>();
        for (JsonNode part : row.path("parts")) {
            String partStatement = text(part, "statement");
            if (partStatement == null) {
                throw new InvalidRecapException("Exercițiul " + key + " are o cerință fără text.");
            }
            parts.add(new RecapPart(partStatement, text(part, "solution"), text(part, "answer"),
                    number(text(part, "result")), part.path("verified").asBoolean(false)));
        }
        if (parts.size() > RecapExercise.MAX_PARTS) {
            throw new InvalidRecapException("Exercițiul " + key + " are prea multe cerințe.");
        }
        exercise.write(statement, text(row, "solution"), text(row, "answer"), number(text(row, "result")), parts);
        String chapter = EnChapterDetector.detect(exercise.fullText());
        if (chapter != null) {
            enChapterRepository.findByCode(chapter).ifPresent(exercise::pointToEnChapter);
        }
        String figure = text(row, "figure");
        if (figure != null && entries.containsKey(figure)) {
            exercise.showImage(store(figure, entries.get(figure)));
        }
        // the zip's own flag, a drawing named but not in the zip, or a text that cites a drawing it was not given
        exercise.markFigureMissing(row.path("figureMissing").asBoolean(false)
                || (figure != null && !entries.containsKey(figure))
                || RecapFigures.mentionsFigure(exercise.fullText()));
        return exercise;
    }

    /** Stores a drawing next to the questions' pictures (a test gets its own copy); removed again on rollback. */
    private String store(String name, byte[] bytes) {
        String type = name.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        try {
            String key = fileService.uploadImage(imagesDir, new BytesUpload(bytes, name.substring(name.lastIndexOf('/') + 1), type));
            AfterTransaction.ifRolledBack(() -> delete(key));
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException("Nu am putut salva figura " + name, e);
        }
    }

    private void delete(String key) {
        try {
            fileService.deleteImage(imagesDir, key);
        } catch (IOException e) {
            log.warn("Could not delete the orphaned recap image {}", key, e);
        }
    }

    /** A plain number a student can type, or null (a radical or a set stays in the answer text only). */
    static String number(String result) {
        return result != null && NumericAnswer.parse(result).isPresent() ? result.strip() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? null : text;
    }

    /** The JSON and the drawings, each within its own cap and all of them within {@code maxTotal} bytes. */
    static Map<String, byte[]> unzip(MultipartFile file, long maxTotal) {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int count = 0;
            long total = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) {
                    throw new InvalidRecapException("Arhiva are prea multe fișiere.");
                }
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                boolean json = name.equals("culegere.json");
                boolean image = name.startsWith("figures/") && !name.contains("..")
                        && name.toLowerCase().matches(".*\\.(png|jpe?g)$");
                if (json || image) {
                    long own = json ? MAX_JSON_BYTES : MAX_IMAGE_BYTES;
                    long left = maxTotal - total;
                    byte[] bytes = read(zip, Math.min(own, left), left < own
                            ? "Arhiva e prea mare: despachetată trece de " + Math.max(1, maxTotal / (1024 * 1024)) + " MB."
                            : "Fișierul " + name + " din arhivă e prea mare.");
                    total += bytes.length;
                    out.put(name, bytes);
                }
            }
        } catch (IOException e) {
            throw new InvalidRecapException("Fișierul nu este o arhivă .zip validă.");
        }
        return out;
    }

    private static byte[] read(InputStream in, long max, String tooLarge) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) > 0) {
            total += n;
            if (total > max) {
                throw new InvalidRecapException(tooLarge);
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** Bytes made or read here, handed to the file service as an upload. */
    record BytesUpload(byte[] bytes, String filename, String type) implements MultipartFile {

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return type;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
