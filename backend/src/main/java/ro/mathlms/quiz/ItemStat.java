package ro.mathlms.quiz;

/**
 * Aggregate of one item's responses across the GRADED attempts of a quiz (see
 * {@code ItemResponseRepository.findItemStatsByQuizId}). A single-choice item nobody answered has no
 * row at all, so rates are computed against the number of graded attempts, not against {@code answered}.
 */
public record ItemStat(Long itemId, Long answered, Long correct, Long totalPoints) {
}
