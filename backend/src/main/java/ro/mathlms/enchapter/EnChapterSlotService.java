package ro.mathlms.enchapter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.content.RoCount;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The automatic links (V34 seeded them from docs/EN-capitole.md): which EN chapter each place on the paper points at
 * when the teacher picked none for the question. The teacher sees and replaces the whole list; questions follow at
 * once, since links are worked out when read.
 */
@Service
public class EnChapterSlotService {

    /**
     * "I.1" .. "III.6": SUBIECTUL I-III, question 1-6; Subiectul III's problems may also have "III.4.a" / "III.4.b"
     * for their requirements (the grile of I and II have none).
     */
    private static final Pattern SLOT = Pattern.compile("^(I|II|III)\\.([1-6])(?:\\.([ab]))?$");
    private static final List<String> SUBJECTS = List.of("I", "II", "III");

    /** One link; {@code code} only to read the list (ignored when saved). */
    public record SlotDto(String slot, Long chapterId, String code) {
    }

    private final EnChapterSlotRepository slotRepository;
    private final EnChapterRepository chapterRepository;
    private final AuditLog auditLog;

    public EnChapterSlotService(EnChapterSlotRepository slotRepository, EnChapterRepository chapterRepository,
                                AuditLog auditLog) {
        this.slotRepository = slotRepository;
        this.chapterRepository = chapterRepository;
        this.auditLog = auditLog;
    }

    /** In paper order: I.1 … I.6, II.1 …, III.1, III.1.a, III.1.b, III.2 … */
    @Transactional(readOnly = true)
    public List<SlotDto> list() {
        return slotRepository.findAllFetched().stream()
                .sorted(Comparator.comparing(EnChapterSlot::getSlot, EnChapterSlotService::paperOrder))
                .map(s -> new SlotDto(s.getSlot(), s.getChapter().getId(), s.getChapter().getCode()))
                .toList();
    }

    /** Replaces every automatic link with {@code slots}; a place left out has no default any more. */
    @Transactional
    public void replace(List<SlotDto> slots) {
        Map<Long, EnChapter> chapters = chapterRepository.findAll().stream()
                .collect(Collectors.toMap(EnChapter::getId, Function.identity()));
        Set<String> seen = new HashSet<>();
        Map<String, EnChapter> wanted = new HashMap<>();
        for (SlotDto slot : slots) {
            String place = slot.slot() == null ? "" : slot.slot().strip();
            Matcher m = SLOT.matcher(place);
            if (!m.matches() || (m.group(3) != null && !m.group(1).equals("III"))) {
                throw new IllegalArgumentException("„" + place + "” nu e un loc din testul EN (ex. I.1, II.6, III.5.b).");
            }
            if (!seen.add(place)) {
                throw new IllegalArgumentException("Locul " + place + " apare de două ori.");
            }
            EnChapter chapter = slot.chapterId() == null ? null : chapters.get(slot.chapterId());
            if (chapter == null) {
                throw new IllegalArgumentException("Capitolul ales pentru " + place + " nu mai există. Alege altul.");
            }
            wanted.put(place, chapter);
        }

        Map<String, EnChapter> before = slotRepository.findAllFetched().stream()
                .collect(Collectors.toMap(EnChapterSlot::getSlot, EnChapterSlot::getChapter));
        List<String> changes = new ArrayList<>();
        Set<String> places = new HashSet<>(before.keySet());
        places.addAll(wanted.keySet());
        places.stream().sorted(EnChapterSlotService::paperOrder).forEach(place -> {
            String was = before.containsKey(place) ? before.get(place).getCode() : "—";
            String now = wanted.containsKey(place) ? wanted.get(place).getCode() : "—";
            if (!Objects.equals(was, now)) {
                changes.add(place + ": " + was + " → " + now);
            }
        });
        if (changes.isEmpty()) {
            return;
        }

        slotRepository.deleteEverything(); // also clears the persistence context: the chapters above are detached now
        slotRepository.saveAll(wanted.entrySet().stream()
                .map(e -> new EnChapterSlot(e.getKey(), chapterRepository.getReferenceById(e.getValue().getId())))
                .toList());
        String details = String.join(", ", changes.subList(0, Math.min(6, changes.size())))
                + (changes.size() > 6 ? " și încă " + (changes.size() - 6) : "");
        auditLog.record(AuditEvent.of(AuditAction.EN_CHAPTER_SLOTS_UPDATED, null,
                "Legături automate EN · " + RoCount.of(changes.size(), "schimbare", "schimbări")).details(details));
    }

    /** I before II before III; then the question; the problem itself before its requirements a), b). */
    static int paperOrder(String a, String b) {
        return Comparator.<String>comparingInt(s -> part(s, 1, SUBJECTS::indexOf))
                .thenComparingInt(s -> part(s, 2, Integer::parseInt))
                .thenComparing(s -> part(s, 3, x -> x == null ? "" : x))
                .compare(a, b);
    }

    private static <T> T part(String slot, int group, Function<String, T> read) {
        Matcher m = SLOT.matcher(slot);
        if (!m.matches()) {
            return read.apply(group == 3 ? null : group == 1 ? "III" : "9"); // unknown places last
        }
        return read.apply(m.group(group));
    }
}
