package ro.mathlms.ingest;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.content.ChapterRepository;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.QuestionImageService;
import ro.mathlms.quiz.QuizAdminService;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The worker step's failure paths: whatever goes wrong, the teacher gets a reason, never a stuck RUNNING row. */
class AiImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private final AiImportRepository repository = mock(AiImportRepository.class);
    private final FileService fileService = mock(FileService.class);
    private final PageExtractor extractor = mock(PageExtractor.class);
    private AiImport queued;

    private AiImportService service(String provider, boolean enabled) {
        return new AiImportService(repository, mock(UserRepository.class), mock(SchoolClassRepository.class),
                mock(ChapterRepository.class), mock(QuizAdminService.class), fileService, mock(AuditLog.class),
                List.of(extractor), new AiImportProperties(enabled, provider, 5, 50, "pages", 7), new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(PlatformTransactionManager.class), noProposals(),
                mock(QuizItemRepository.class), mock(QuestionImageService.class));
    }

    /** No EN chapter proposals: the questions go to review as they were read. */
    private static ChapterSuggestionService noProposals() {
        ChapterSuggestionService suggestions = mock(ChapterSuggestionService.class);
        when(suggestions.annotate(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return suggestions;
    }

    @BeforeEach
    void setUp() throws Exception {
        queued = new AiImport(1L, null, null, 1, "[{\"key\":\"p1.jpg\",\"type\":\"image/jpeg\"}]", NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(queued, "id", 7L);
        when(repository.claimNext()).thenReturn(Optional.of(queued));
        when(repository.findById(any())).thenReturn(Optional.of(queued));
        when(fileService.loadImage(anyString(), anyString())).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));
        when(extractor.provider()).thenReturn("openai");
    }

    @Test
    void theProvidersOwnMessageReachesTheTeacher() throws Exception {
        when(extractor.extract(any())).thenThrow(new ExtractionException("Poza e prea neclară."));

        assertThat(service("openai", true).processNext()).isTrue();

        assertThat(queued.getStatus()).isEqualTo(AiImportStatus.FAILED);
        assertThat(queued.getError()).isEqualTo("Poza e prea neclară.");
        assertThat(queued.getAttempts()).isEqualTo(1);
    }

    @Test
    void anUnexpectedErrorBecomesAPlainReason() throws Exception {
        when(extractor.extract(any())).thenThrow(new IllegalStateException("HTTP 500 from provider"));

        service("openai", true).processNext();

        assertThat(queued.getError()).isEqualTo(AiImportService.UNREADABLE);
    }

    @Test
    void pagesWithNoQuestionsFailWithAdviceOnTakingThePhoto() throws Exception {
        when(extractor.extract(any())).thenReturn(new PageExtractor.ExtractionResult(List.of(), "m", 10, 1));

        service("openai", true).processNext();

        assertThat(queued.getError()).isEqualTo(AiImportService.NOTHING_FOUND);
    }

    @Test
    void anUnknownProviderFailsTheReadInsteadOfHanging() {
        service("nimeni", true).processNext();

        assertThat(queued.getError()).isEqualTo(AiImportService.NO_PROVIDER);
    }

    @Test
    void whatWasReadIsStoredWithItsCost() throws Exception {
        when(extractor.extract(any())).thenReturn(new PageExtractor.ExtractionResult(List.of(
                new ExtractedItem(1, null, ro.mathlms.quiz.QuizItemType.OPEN, "Rezolvă", List.of(), 10, false, List.of())),
                "gpt-x", 1200, 300));

        service("openai", true).processNext();

        assertThat(queued.getStatus()).isEqualTo(AiImportStatus.DONE);
        assertThat(queued.getProvider()).isEqualTo("openai");
        assertThat(queued.getModel()).isEqualTo("gpt-x");
        assertThat(queued.getInputTokens()).isEqualTo(1200);
        assertThat(queued.getItemsJson()).contains("Rezolvă");
    }

    @Test
    void anEmptyQueueDoesNothing() {
        when(repository.claimNext()).thenReturn(Optional.empty());

        assertThat(service("openai", true).processNext()).isFalse();
    }

    @Test
    void switchedOffTheFeatureIsNotThere() {
        AiImportService off = service("openai", false);

        assertThat(off.settings("prof@scoala.ro").enabled()).isFalse();
        assertThatThrownBy(() -> off.list("prof@scoala.ro")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> off.get("prof@scoala.ro", 1L)).isInstanceOf(ResponseStatusException.class);
    }
}
