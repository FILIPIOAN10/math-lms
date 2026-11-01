package ro.mathlms.quiz;

/** One student's choice on one grilă of a paper that counts: what {@link QuizStatsService} counts per option. */
public record OptionChoice(Long attemptId, Long itemId, Long optionId, Boolean correct) {
}
