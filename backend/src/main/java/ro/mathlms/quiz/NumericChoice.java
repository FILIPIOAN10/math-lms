package ro.mathlms.quiz;

/** One number typed on a paper that counts: what {@link QuizStatsService} groups by value. */
public record NumericChoice(Long itemId, String value, Boolean correct) {
}
