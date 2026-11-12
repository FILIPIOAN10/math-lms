package ro.mathlms.review;

import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemHint;
import ro.mathlms.quiz.QuizItemHintRepository;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.review.ReviewDtos.ReviewOptionDto;
import ro.mathlms.review.ReviewDtos.ReviewQuestionDto;

import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * The cards of the daily review and of a chapter's practice, built the same way: statement, options in their order,
 * the teacher's hints. Options and hints come in one query each for all the cards (audit 2026-11-12 L2: it was two
 * queries per card). The right option is never part of a card - it comes with the answer.
 */
final class PracticeQuestions {

    private final QuizOptionRepository optionRepository;
    private final QuizItemHintRepository hintRepository;

    PracticeQuestions(QuizOptionRepository optionRepository, QuizItemHintRepository hintRepository) {
        this.optionRepository = optionRepository;
        this.hintRepository = hintRepository;
    }

    /** One card per item, in the order given; {@code box} = the Leitner box each card shows (0 outside the review). */
    List<ReviewQuestionDto> of(List<QuizItem> items, ToIntFunction<QuizItem> box) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> ids = items.stream().map(QuizItem::getId).toList();
        Map<Long, List<ReviewOptionDto>> options = optionRepository.findByItemIdInOrderByItemIdAscPositionAsc(ids).stream()
                .collect(Collectors.groupingBy(o -> o.getItem().getId(),
                        Collectors.mapping(PracticeQuestions::option, Collectors.toList())));
        Map<Long, List<String>> hints = hintRepository.findByItemIdInOrderByItemIdAscPositionAsc(ids).stream()
                .collect(Collectors.groupingBy(h -> h.getItem().getId(),
                        Collectors.mapping(QuizItemHint::getText, Collectors.toList())));
        return items.stream()
                .map(item -> new ReviewQuestionDto(item.getId(), item.getQuiz().getTitle(), item.getStatement(),
                        options.getOrDefault(item.getId(), List.of()), box.applyAsInt(item), item.getImageKey(),
                        hints.getOrDefault(item.getId(), List.of())))
                .toList();
    }

    private static ReviewOptionDto option(QuizOption option) {
        return new ReviewOptionDto(option.getId(), option.getPosition(), option.getText());
    }
}
