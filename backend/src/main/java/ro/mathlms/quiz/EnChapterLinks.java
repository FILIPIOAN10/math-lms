package ro.mathlms.quiz;

import org.springframework.stereotype.Component;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterLinkDto;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.enchapter.EnChapterService;
import ro.mathlms.enchapter.EnChapterSlot;
import ro.mathlms.enchapter.EnChapterSlotRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which EN chapter page each question (and each requirement a), b)...) points at (V34). The teacher's own choice wins;
 * otherwise the default for its place on the paper: "SUBIECTUL I", question 1 is slot "I.1" → A1, requirement b) of
 * problem 6 under "SUBIECTUL al III-lea" is "III.6.b" → G9, and a requirement without a slot of its own follows its
 * problem. Worked out when read, from the questions as they are now, so reordering never leaves a stale link behind;
 * a test without EN headings simply has no defaults.
 */
@Component
public class EnChapterLinks {

    private final EnChapterSlotRepository slotRepository;
    private final EnChapterRepository chapterRepository;
    private final EnChapterService chapterService;

    public EnChapterLinks(EnChapterSlotRepository slotRepository, EnChapterRepository chapterRepository,
                          EnChapterService chapterService) {
        this.slotRepository = slotRepository;
        this.chapterRepository = chapterRepository;
        this.chapterService = chapterService;
    }

    /** The links of one quiz's questions, all of them in position order (the numbering needs every one). */
    public Resolved resolve(List<QuizItem> items) {
        Map<String, EnChapter> slots = slotRepository.findAllFetched().stream()
                .collect(Collectors.toMap(EnChapterSlot::getSlot, EnChapterSlot::getChapter));
        Map<Long, String> numbers = QuestionNumbers.of(items);
        return new Resolved(items.stream().collect(Collectors.toMap(QuizItem::getId, Function.identity())),
                numbers, slots, chapterService.readyIds());
    }

    /** The chapter the teacher picked, or {@code null} for "automatic"; an unknown id is a mistake in the request. */
    public EnChapter chosen(Long enChapterId) {
        if (enChapterId == null) {
            return null;
        }
        return chapterRepository.findById(enChapterId)
                .orElseThrow(() -> new InvalidQuizException("Capitolul EN ales nu mai există. Alege altul din listă."));
    }

    static Long idOf(EnChapter chapter) {
        return chapter == null ? null : chapter.getId();
    }

    /** One quiz's links, ready to be asked about each question and requirement. */
    public static final class Resolved {

        private final Map<Long, QuizItem> items;
        private final Map<Long, String> numbers;
        private final Map<String, EnChapter> slots;
        private final Set<Long> ready;

        Resolved(Map<Long, QuizItem> items, Map<Long, String> numbers, Map<String, EnChapter> slots, Set<Long> ready) {
            this.items = new HashMap<>(items);
            this.numbers = numbers;
            this.slots = slots;
            this.ready = ready;
        }

        /** What "automatic" means for this question: the default for its place, or null when it has none. */
        EnChapter itemDefault(QuizItem item) {
            String number = numbers.get(item.getId());
            return number == null ? null : slots.get(number);
        }

        /** Where the question points: the teacher's choice, else the default for its place. */
        public EnChapter itemChapter(QuizItem item) {
            return item.getEnChapter() != null ? item.getEnChapter() : itemDefault(item);
        }

        /** What "automatic" means for this requirement: its own slot, else wherever its problem points. */
        EnChapter partDefault(QuizItemPart part) {
            QuizItem item = items.getOrDefault(part.getItem().getId(), part.getItem());
            String number = numbers.get(item.getId());
            EnChapter own = number == null ? null : slots.get(number + "." + part.label());
            return own != null ? own : itemChapter(item);
        }

        public EnChapter partChapter(QuizItemPart part) {
            return part.getEnChapter() != null ? part.getEnChapter() : partDefault(part);
        }

        EnChapterLinkDto linkOf(QuizItem item) {
            EnChapter chapter = itemChapter(item);
            return EnChapterLinkDto.of(chapter, chapter != null && ready.contains(chapter.getId()));
        }

        EnChapterLinkDto linkOf(QuizItemPart part) {
            EnChapter chapter = partChapter(part);
            return EnChapterLinkDto.of(chapter, chapter != null && ready.contains(chapter.getId()));
        }
    }
}
