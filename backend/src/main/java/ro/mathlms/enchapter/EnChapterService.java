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
import ro.mathlms.enchapter.EnChapterDtos.SectionDto;

import java.util.ArrayList;
import java.util.Collections;
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

    /** Enough for a long chapter (one PDF of the book, up to 40 pages); more is a sign of the wrong photos going up. */
    static final int MAX_PHOTOS = 40;
    static final int MAX_EXAMPLES = 30;

    private final EnChapterRepository chapterRepository;
    private final EnChapterPhotoRepository photoRepository;
    private final EnChapterExampleRepository exampleRepository;
    private final EnChapterSlotRepository slotRepository;
    private final EnChapterImages images;
    private final AuditLog auditLog;

    public EnChapterService(EnChapterRepository chapterRepository, EnChapterPhotoRepository photoRepository,
                            EnChapterExampleRepository exampleRepository, EnChapterSlotRepository slotRepository,
                            EnChapterImages images, AuditLog auditLog) {
        this.chapterRepository = chapterRepository;
        this.slotRepository = slotRepository;
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

    /**
     * The pages a student can open to something: those with photos or worked examples, and every section and point
     * under one of them (they open that chapter at their page).
     */
    @Transactional(readOnly = true)
    public Set<Long> readyIds() {
        Set<Long> withContent = new HashSet<>(counts(photoRepository.countPerChapter()).keySet());
        withContent.addAll(counts(exampleRepository.countPerChapter()).keySet());
        Set<Long> ready = new HashSet<>(withContent);
        if (withContent.isEmpty()) {
            return ready;
        }
        EnChapterTree tree = new EnChapterTree(chapterRepository.findAllByOrderByPositionAscIdAsc());
        for (EnChapter chapter : chapterRepository.findAllById(withContent)) {
            tree.descendantsOf(chapter).forEach(under -> ready.add(under.getId()));
        }
        return ready;
    }

    @Transactional(readOnly = true)
    public EnChapterPageDto page(Long id) {
        EnChapter chapter = requireChapter(id);
        EnChapterTree tree = new EnChapterTree(chapterRepository.findAllByOrderByPositionAscIdAsc());
        EnChapter root = tree.rootOf(chapter);
        return new EnChapterPageDto(chapter.getId(), chapter.getCode(), chapter.getTitle(), chapter.getArea(),
                chapter.getNotes(), chapter.getParentId(), root.getId(), chapter.getBookPage(),
                tree.descendantsOf(chapter).stream().map(SectionDto::of).toList(),
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
        requireParent(request.parentId(), request.area(), null);
        EnChapter created = new EnChapter(code, request.title(), request.area(),
                chapterRepository.maxPosition() + 1, request.notes());
        created.placeIn(request.parentId(), request.bookPage());
        EnChapter saved = chapterRepository.save(created);
        auditLog.record(AuditEvent.of(AuditAction.EN_CHAPTER_CREATED, saved.getId(), label(saved)));
        return EnChapterSummaryDto.of(saved, 0, 0);
    }

    @Transactional
    public EnChapterSummaryDto update(Long id, EnChapterRequest request) {
        EnChapter chapter = requireChapter(id);
        String code = EnChapter.normalizeCode(request.code());
        requireFreeCode(code, chapter);
        requireParent(request.parentId(), request.area(), chapter);
        String before = label(chapter);
        String notesBefore = chapter.getNotes();
        String placeBefore = place(chapter);
        chapter.update(code, request.title(), request.area(), request.notes());
        chapter.placeIn(request.parentId(), request.bookPage());
        String details = new AuditText.Changes()
                .addIfDifferent(before, label(chapter), "fost „" + before + "”")
                .addIfDifferent(notesBefore, chapter.getNotes(), "textul paginii")
                .addIfDifferent(placeBefore, place(chapter), "locul în carte")
                .joined();
        auditLog.record(AuditEvent.of(AuditAction.EN_CHAPTER_UPDATED, id, label(chapter)).details(details));
        return EnChapterSummaryDto.of(chapter, photoRepository.countByChapterId(id), exampleRepository.countByChapterId(id));
    }

    /**
     * Only an empty page goes, so a slip of the finger never throws away photographed theory - and only one no place on
     * the paper points at by default, or those questions would quietly lose their link. Questions the teacher pointed
     * at it themselves go back to automatic; the journal says how many.
     */
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
        long sections = new EnChapterTree(chapterRepository.findAllByOrderByPositionAscIdAsc())
                .descendantsOf(chapter).size();
        if (sections > 0) {
            throw new EnChapterConflictException("Capitolul „" + label(chapter) + "” are "
                    + RoCount.of(sections, "subcapitol", "subcapitole") + ". Șterge-le întâi, apoi capitolul.");
        }
        List<String> slots = slotRepository.findSlotsOfChapter(id).stream()
                .sorted(EnChapterSlotService::paperOrder).toList();
        if (!slots.isEmpty()) {
            throw new EnChapterConflictException("Capitolul „" + label(chapter) + "” e legătura automată pentru "
                    + String.join(", ", slots) + ". Alege alt capitol pentru aceste locuri în „Legături automate”, "
                    + "apoi șterge-l.");
        }
        long chosen = chapterRepository.countChosenBy(id);
        chapterRepository.delete(chapter);
        AuditEvent event = AuditEvent.of(AuditAction.EN_CHAPTER_DELETED, id, label(chapter));
        auditLog.record(chosen == 0 ? event : event.details(
                RoCount.of(chosen, "întrebare sau cerință", "întrebări sau cerințe")
                        + (chosen == 1 ? " revine" : " revin") + " la legătura automată"));
    }

    /**
     * Swaps the page with its neighbour above or below among its siblings - the other chapters of its own part
     * (algebra or geometry - the pages list them apart), or the other sections of its chapter; at either end nothing
     * changes. A chapter takes its sections with it. Layout only: not journalled.
     */
    @Transactional
    public void move(Long id, Direction direction) {
        EnChapter chapter = requireChapter(id);
        EnChapterTree tree = new EnChapterTree(chapterRepository.findAllByOrderByPositionAscIdAsc());
        EnChapter parent = tree.parentOf(chapter);
        List<EnChapter> order = new ArrayList<>(parent == null ? tree.roots() : tree.childrenOf(parent));
        List<EnChapter> siblings = order.stream().filter(c -> c.getArea() == chapter.getArea()).toList();
        int index = siblings.indexOf(chapter);
        int other = direction == Direction.UP ? index - 1 : index + 1;
        if (other >= 0 && other < siblings.size()) {
            Collections.swap(order, order.indexOf(chapter), order.indexOf(siblings.get(other)));
        }
        // Renumber the whole list 1..n in book order (a chapter, then its sections, then the next chapter): positions
        // may have gaps or ties after deletions, and a moved chapter's sections must follow it.
        List<EnChapter> numbered = new ArrayList<>();
        for (EnChapter root : parent == null ? order : tree.roots()) {
            appendInBookOrder(root, tree, parent, order, numbered);
        }
        for (int i = 0; i < numbered.size(); i++) {
            numbered.get(i).moveTo(i + 1);
        }
    }

    /** Pre-order walk where the children of {@code reorderedParent} come in the {@code reordered} order. */
    private static void appendInBookOrder(EnChapter page, EnChapterTree tree, EnChapter reorderedParent,
                                          List<EnChapter> reordered, List<EnChapter> out) {
        out.add(page);
        List<EnChapter> kids = reorderedParent != null && page.getId().equals(reorderedParent.getId())
                ? reordered : tree.childrenOf(page);
        for (EnChapter kid : kids) {
            appendInBookOrder(kid, tree, reorderedParent, reordered, out);
        }
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

    /** "9:null" / "12:5": where a page sits, for the journal. */
    private static String place(EnChapter chapter) {
        return "pagina " + chapter.getBookPage() + ", sub " + chapter.getParentId();
    }

    /**
     * A parent must exist, be in the same part (algebra or geometry), leave room for the page and what is under it
     * (chapter, section, point: nothing deeper), and not be the page itself or one of its own sections.
     */
    private void requireParent(Long parentId, EnArea area, EnChapter self) {
        if (parentId == null) {
            return;
        }
        EnChapter parent = chapterRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Capitolul-părinte nu există."));
        EnChapterTree tree = new EnChapterTree(chapterRepository.findAllByOrderByPositionAscIdAsc());
        if (parent.getArea() != area) {
            throw new IllegalArgumentException("Subcapitolul trebuie să fie în aceeași parte (algebră sau geometrie) "
                    + "ca „" + label(parent) + "”.");
        }
        if (self != null && tree.isSelfOrAncestor(self, parent)) {
            throw new IllegalArgumentException("Un capitol nu poate fi sub propriile subcapitole.");
        }
        int below = self == null ? 0 : tree.heightOf(self);
        if (tree.depthOf(parent) + 1 + below > EnChapterTree.MAX_DEPTH) {
            throw new IllegalArgumentException("Capitol, secțiune, punct: mai adânc de atât nu merge.");
        }
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
