package ro.mathlms.quiz;

/** How many hints one item has - the result of the per-quiz count query (one query for all of a quiz's items). */
public record HintCount(Long itemId, Long total) {
}
