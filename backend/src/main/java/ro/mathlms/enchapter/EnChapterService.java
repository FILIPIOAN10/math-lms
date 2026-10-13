package ro.mathlms.enchapter;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;
import ro.mathlms.content.RoCount;
import ro.mathlms.enchapter.EnChapterDtos.Direction;
import ro.mathlms.enchapter.EnChapterDtos.EnChapterPageDto;
import ro.mathlms.enchapter.EnChapterDtos.EnChapterRequest;
import ro.mathlms.enchapter.EnChapterDtos.EnChapterSummaryDto;
import ro.mathlms.enchapter.EnChapterDtos.ExampleDto;
import ro.mathlms.enchapter.EnChapterDtos.ExampleRequest;
import ro.mathlms.enchapter.EnChapterDtos.PhotoDto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The EN chapter pages: the list (seeded by V33, editable by the teacher) and each page's theory photos and worked
 * examples. Every active account reads them - they are teaching material for the whole EN, not tied to a class.
 * Every write is journalled in the same transaction.
 */
@Service
public class EnChapterService {

    /** Enough for a chapter's theory; more is a sign of the wrong photos going up. */
    static final int MAX_PHOTOS = 20;
    static final int MAX_EXAMPLES = 30;

    private final EnChapterRepository chapterRepository;
    private final EnChapterPhotoRepository photoRepository;
    private final EnChapterExampleRepository exampleRepository;
    private final EnChapterImages images;
    private final AuditLog auditLog;

    public EnChapterService(EnChapterRepository chapterRepository, EnChapterPhotoRepository photoRepository,
                            EnChapterExampleRepository exampleRepository, EnChapterImages images, AuditLog auditLog) {
        this.chapterRepository = chapterRepository;
        this.photoRepository = photoRepository;
        this.exampleRepository = exampleRepository;
        this.images = images;
        this.auditLog = auditLog;
    }

    /** "A1 · Ordinea efectuării operațiilor": a chapter as the journal names it. */
    private static String label(EnChapter chapter) {
        return chapter.getCode() + " · " + chapter.getTitle();
    }

    private static String label(EnChapterExample example) {
        return label(example.getChapter()) + " · model: " + AuditText.snippet(example.getStatement(), 60);
    }

    // --- reading (every active account) ---

    @Transactional(readOnly = true)
    public List<EnChapterSummaryDto> list() {
        Map<Long, Long> photos = counts(photoRepository.countPerChapter());
        Map<Long, Long> examples = counts(exampleRepository.countPerChapter());
        return chapterRepository.findAllByOrderByPositionAscIdAsc().stream()
                .map(c -> EnChapterSummaryDto.of(c, photos.getOrDefault(c.getId(), 0L), examples.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    /** The chapters whose page has anything on it (photos or worked examples). */
    @Transactional(readOnly = true)
    public Set<Long> readyIds() {
        Set<Long> ready = new HashSet<>(counts(photoRepository.countPerChapter()).keySet());
        ready.addAll(counts(exampleRepository.countPerChapter()).keySet());
        return ready;
    }

    @Transactional(readOnly = true)
    public EnChapterPageDto page(Long id) {
        EnChapter chapter = requireChapter(id);
        return new EnChapterPageDto(chapter.getId(), chapter.getCode(), chapter.getTitle(), chapter.getArea(),
                chapter.getNotes(),
                photoRepository.findByChapterIdOrderByPositionAscIdAsc(id).stream().map(p -> new PhotoDto(p.getId())).toList(),
                exampleRepository.findByChapterIdOrderByPositionAscIdAsc(id).stream().map(ExampleDto::from).toList());
    }

    @Transactional(readOnly = true)
    public Resource photo(Long chapterId, Long photoId) {
        EnChapterPhoto photo = photoRepository.findByIdAndChapterId(photoId, chapterId)
                .orElseThrow(() -> new EnChapterNotFoundException("photo", photoId));
        return images.load(photo.getImageKey()).orElseThrow(() -> new EnChapterNotFoundException("photo", photoId));
    }

    @Transactional(readOnly = true)
    public Resource exampleImage(Long chapterId, Long exampleId) {
        EnChapterExample example = requireExample(chapterId, exampleId);
        if (example.getImageKey() == null) {
            throw new EnChapterNotFoundException("example image", exampleId);
        }
        return images.load(example.getImageKey()).orElseThrow(() -> new EnChapterNotFoundException("example image", exampleId));
    }

    // --- the list (teacher) ---

    @Transactional
    public EnChapterSummaryDto create(EnChapterRequest request) {
        String code = EnChapter.normalizeCode(request.code());
        requireFreeCode(code, null);
        EnChapter saved = chapterRepository.save(new EnChapter(code, request.title(), request.area(),
                chapterRepository.maxPosition() + 1, request.notes()));
        auditLog.record(AuditEvent.of(AuditAction.EN_CHAPTER_CREATED, saved.getId(), label(saved)));
        return EnChapterSummaryDto.of(saved, 0, 0);
    }

    @Transactional
    public EnChapterSummaryDto update(Long id, EnChapterRequest request) {
        EnChapter chapter = requireChapter(id);
        String code = EnChapter.normalizeCode(request.code());
        requireFreeCode(code, chapter);
        String before = label(chapter);
        String notesBefore = chapter.getNotes();
        chapter.update(code, request.title(), request.area(), request.notes());
        String details = new AuditText.Changes()
                .addIfDifferent(before, label(chapter), "fost „" + before + "”")
                .addIfDifferent(notesBefore, chapter.getNotes(), "textul paginii")
                .joined();
        auditLog.record(AuditEvent.of(AuditAction.EN_CHAPTER_UPDATED, id, label(chapter)).details(details));
        return EnChapterSummaryDto.of(chapter, photoRepository.countByChapterId(id), exampleRepository.countByChapterId(id));
    }

    /** Only an empty page goes, so a slip of the finger never throws away photographed theory. */
    @Transactional
    public void delete(Long id) {
        EnChapter chapter = requireChapter(id);
        long photos = photoRepository.countByChapterId(id);
        long examples = exampleRepository.countByChapterId(id);
        if (photos > 0 || examples > 0) {
            throw new EnChapterConflictException("Capitolul „" + label(chapter) + "” are "
                    + RoCount.of(photos, "poză", "poze") + " și " + RoCount.of(examples, "model", "modele")
                    + ". Șterge-le întâi, apoi capitolul.");
        }
        chapterRepository.delete(chapter);
        auditLog.record(AuditEvent.of(AuditAction.EN_CHAPTER_DELETED, id, label(chapter)));
    }

    /**
     * Swaps the chapter with its neighbour above or below in its own part (algebra or geometry - the pages list them
     * apart); at either end nothing changes. Layout only: not journalled.
     */
    @Transactional
    public void move(Long id, Direction direction) {
        EnChapter chapter = requireChapter(id);
        List<EnChapter> all = chapterRepository.findAllByOrderByPositionAscIdAsc();
        // Renumber the whole list 1..n first: positions may have gaps or ties after deletions.
        for (int i = 0; i < all.size(); i++) {
            all.get(i).moveTo(i + 1);
        }
        List<EnChapter> sameArea = all.stream().filter(c -> c.getArea() == chapter.getArea()).toList();
        int index = sameArea.indexOf(chapter);
        int other = direction == Direction.UP ? index - 1 : index + 1;
        if (other < 0 || other >= sameArea.size()) {
            return;
        }
        EnChapter neighbour = sameArea.get(other);
        int mine = chapter.getPosition();
        chapter.moveTo(neighbour.getPosition());
        neighbour.moveTo(mine);
    }

    // --- theory photos (teacher) ---

    @Transactional
    public PhotoDto addPhoto(Long chapterId, MultipartFile file) {
        EnChapter chapter = requireChapter(chapterId);
        if (photoRepository.countByChapterId(chapterId) >= MAX_PHOTOS) {
            throw new IllegalArgumentException("O pagină poate avea cel mult " + MAX_PHOTOS + " de poze.");
        }
        String stored = images.store(file);
        EnChapterPhoto saved = photoRepository.save(
                new EnChapterPhoto(chapter, photoRepository.maxPosition(chapterId) + 1, stored));
        auditLog.record(AuditEvent.of(AuditAction.EN_CHAPTER_PHOTO_ADDED, chapterId, label(chapter)));
        return new PhotoDto(saved.getId());
    }

    @Transactional
    public void removePhoto(Long chapterId, Long photoId) {
        EnChapterPhoto photo = photoRepository.findByIdAndChapterId(photoId, chapterId)
                .orElseThrow(() -> new EnChapterNotFoundException("photo", photoId));
        photoRepository.delete(photo);
        images.deleteAfterCommit(photo.getImageKey());
        auditLog.record(AuditEvent.of(AuditAction.EN_CHAPTER_PHOTO_REMOVED, chapterId, label(photo.getChapter())));
    }

    // --- worked examples (teacher) ---

    @Transactional
    public ExampleDto addExample(Long chapterId, ExampleRequest request) {
        EnChapter chapter = requireChapter(chapterId);
        if (exampleRepository.countByChapterId(chapterId) >= MAX_EXAMPLES) {
            throw new IllegalArgumentException("O pagină poate avea cel mult " + MAX_EXAMPLES + " de modele.");
        }
        EnChapterExample saved = exampleRepository.save(new EnChapterExample(chapter,
                exampleRepository.maxPosition(chapterId) + 1, request.statement(), request.solution()));
        auditLog.record(AuditEvent.of(AuditAction.EN_EXAMPLE_ADDED, chapterId, label(saved)));
        return ExampleDto.from(saved);
    }

    @Transactional
    public ExampleDto updateExample(Long chapterId, Long exampleId, ExampleRequest request) {
        EnChapterExample example = requireExample(chapterId, exampleId);
        String details = new AuditText.Changes()
                .addIfDifferent(example.getStatement(), request.statement().strip(), "enunț")
                .addIfDifferent(example.getSolution(), request.solution().strip(), "rezolvare")
                .joined();
        example.update(request.statement(), request.solution());
        auditLog.record(AuditEvent.of(AuditAction.EN_EXAMPLE_UPDATED, chapterId, label(example)).details(details));
        return ExampleDto.from(example);
    }

    @Transactional
    public void deleteExample(Long chapterId, Long exampleId) {
        EnChapterExample example = requireExample(chapterId, exampleId);
        exampleRepository.delete(example);
        images.deleteAfterCommit(example.getImageKey());
        auditLog.record(AuditEvent.of(AuditAction.EN_EXAMPLE_DELETED, chapterId, label(example)));
    }

    @Transactional
    public void setExampleImage(Long chapterId, Long exampleId, MultipartFile file) {
        EnChapterExample example = requireExample(chapterId, exampleId);
        String stored = images.store(file);
        images.deleteAfterCommit(example.replaceImage(stored));
        auditLog.record(AuditEvent.of(AuditAction.EN_EXAMPLE_UPDATED, chapterId, label(example)).details("figură"));
    }

    @Transactional
    public void removeExampleImage(Long chapterId, Long exampleId) {
        EnChapterExample example = requireExample(chapterId, exampleId);
        String removed = example.removeImage();
        if (removed != null) {
            images.deleteAfterCommit(removed);
            auditLog.record(AuditEvent.of(AuditAction.EN_EXAMPLE_UPDATED, chapterId, label(example))
                    .details("figură ștearsă"));
        }
    }

    // --- helpers ---

    private EnChapter requireChapter(Long id) {
        return chapterRepository.findById(id).orElseThrow(() -> new EnChapterNotFoundException("EnChapter", id));
    }

    private EnChapterExample requireExample(Long chapterId, Long exampleId) {
        return exampleRepository.findByIdAndChapterId(exampleId, chapterId)
                .orElseThrow(() -> new EnChapterNotFoundException("example", exampleId));
    }

    /** A code belongs to one chapter only ({@code self} may keep its own). */
    private void requireFreeCode(String code, EnChapter self) {
        chapterRepository.findByCode(code)
                .filter(other -> self == null || !other.getId().equals(self.getId()))
                .ifPresent(other -> {
                    throw new EnChapterConflictException("Codul " + code + " e deja folosit de „" + other.getTitle() + "”.");
                });
    }

    private static Map<Long, Long> counts(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
