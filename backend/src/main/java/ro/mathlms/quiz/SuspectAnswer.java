package ro.mathlms.quiz;

import ro.mathlms.quiz.QuizAttemptRepository.CountedAttemptRow;
import ro.mathlms.quiz.QuizStatsDtos.SuspectDto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Whether the answer marked right on a grilă looks like the teacher's slip: the strongest students agree on another
 * option. "Strongest" is measured on the rest of the test, so a wrong key does not push down the very students who
 * answered it well. Only a warning, worked out from the answers alone: the teacher decides.
 */
final class SuspectAnswer {

    /** With fewer students a few answers prove nothing. */
    static final int MIN_STUDENTS = 6;

    private static final int MIN_STRONG = 3;

    private SuspectAnswer() {
    }

    static Optional<SuspectDto> find(List<QuizOption> options, List<OptionChoice> choices,
                                     List<CountedAttemptRow> counted, int points) {
        if (counted.size() < MIN_STUDENTS || options.stream().noneMatch(QuizOption::isCorrect)) {
            return Optional.empty();
        }
        Map<Long, OptionChoice> byAttempt = choices.stream()
                .collect(Collectors.toMap(OptionChoice::attemptId, Function.identity()));
        int strong = Math.max(MIN_STRONG, (counted.size() + 2) / 3);
        Map<Long, Long> chosen = counted.stream()
                .sorted(Comparator.comparingInt((CountedAttemptRow row) -> restOfTest(row, byAttempt, points)).reversed())
                .limit(strong)
                .map(row -> byAttempt.get(row.getAttemptId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(OptionChoice::optionId, Collectors.counting()));
        long right = options.stream().filter(QuizOption::isCorrect)
                .mapToLong(o -> chosen.getOrDefault(o.getId(), 0L)).sum();
        return options.stream()
                .filter(o -> !o.isCorrect())
                .max(Comparator.comparingLong(o -> chosen.getOrDefault(o.getId(), 0L)))
                .filter(o -> {
                    long votes = chosen.getOrDefault(o.getId(), 0L);
                    return votes * 2 >= strong && votes > right;
                })
                .map(o -> new SuspectDto(QuizAttemptService.letterOf(options, o), chosen.get(o.getId()).intValue(), strong));
    }

    /** The paper's points without this question's. */
    private static int restOfTest(CountedAttemptRow row, Map<Long, OptionChoice> byAttempt, int points) {
        OptionChoice choice = byAttempt.get(row.getAttemptId());
        return row.getScore() - (choice != null && Boolean.TRUE.equals(choice.correct()) ? points : 0);
    }
}
