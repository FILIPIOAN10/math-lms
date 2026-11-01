package ro.mathlms.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.ingest.MistakeSuggester.Question;
import ro.mathlms.quiz.InvalidQuizException;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MistakeSuggestionServiceTest {

    private final QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
    private final QuizItem item = withId(new QuizItem(new Quiz("Teză", null), 0, QuizItemType.SINGLE_CHOICE, "1/2 + 1/4 =", 5, null), 1L);
    private final QuizOption right = withId(new QuizOption(item, 0, "3/4", true), 10L);
    private final QuizOption wrong = withId(new QuizOption(item, 1, "2/6", false), 11L);
    private final List<Question> asked = new ArrayList<>();

    private final MistakeSuggester suggester = new MistakeSuggester() {
        @Override
        public String provider() {
            return "gemini";
        }

        @Override
        public String suggest(Question question) throws ExtractionException {
            asked.add(question);
            if (question.wrong().equals("sparge")) {
                throw new ExtractionException("Gemini nu răspunde acum.");
            }
            return "Ai adunat numitorii.";
        }
    };

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private MistakeSuggestionService service(boolean enabled, String provider) {
        when(optionRepository.findById(10L)).thenReturn(Optional.of(right));
        when(optionRepository.findById(11L)).thenReturn(Optional.of(wrong));
        when(optionRepository.findByItemIdOrderByPosition(1L)).thenReturn(List.of(right, wrong));
        return new MistakeSuggestionService(List.of(suggester), new AiImportProperties(enabled, provider, 5, 50, "pages", 7),
                optionRepository, mock(PlatformTransactionManager.class));
    }

    @Test
    void theAiReadsTheQuestionTheRightAnswerAndTheWrongOption() {
        assertThat(service(true, "gemini").suggest(11L).text()).isEqualTo("Ai adunat numitorii.");

        assertThat(asked).containsExactly(new Question("1/2 + 1/4 =", "3/4", "2/6"));
    }

    @Test
    void aServerWithoutTheAiSaysItDoesNotExist() {
        assertThatThrownBy(() -> service(false, "gemini").suggest(11L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void anUnknownProviderIsToldInWords() {
        assertThatThrownBy(() -> service(true, "altceva").suggest(11L)).isInstanceOf(ChapterSuggestionException.class);
    }

    @Test
    void theRightOptionHasNothingToPropose() {
        assertThatThrownBy(() -> service(true, "gemini").suggest(10L)).isInstanceOf(InvalidQuizException.class);
    }

    @Test
    void aFailedCallKeepsItsMessageForTheTeacher() {
        MistakeSuggestionService service = service(true, "gemini");
        QuizOption broken = withId(new QuizOption(item, 2, "sparge", false), 12L);
        when(optionRepository.findById(12L)).thenReturn(Optional.of(broken));

        assertThatThrownBy(() -> service.suggest(12L)).isInstanceOf(ChapterSuggestionException.class)
                .hasMessage("Gemini nu răspunde acum.");
    }

    @Test
    void anEmptyAnswerIsAnErrorAndALongOneIsCut() throws ExtractionException {
        assertThatThrownBy(() -> MistakeReading.cleaned("   ")).isInstanceOf(ExtractionException.class);

        assertThat(MistakeReading.cleaned("  x  ")).isEqualTo("x");
        assertThat(MistakeReading.cleaned("a".repeat(900))).hasSize(MistakeReading.MAX_LENGTH);
    }

    @Test
    void theQuestionTextSaysWhenNoAnswerIsMarked() {
        assertThat(MistakeReading.questionText(new Question("1 + 1", null, "3"))).contains("(nemarcat)").contains("3");
    }
}
