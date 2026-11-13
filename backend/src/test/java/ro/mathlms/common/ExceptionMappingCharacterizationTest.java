package ro.mathlms.common;

import io.jsonwebtoken.JwtException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.assignment.AssignmentConflictException;
import ro.mathlms.assignment.AssignmentNotFoundException;
import ro.mathlms.assignment.InvalidAssignmentException;
import ro.mathlms.auth.AccountNotActiveException;
import ro.mathlms.auth.CustomOidcUserService;
import ro.mathlms.auth.EmailAlreadyRegisteredException;
import ro.mathlms.auth.EmailNotAllowedException;
import ro.mathlms.auth.InvalidRefreshTokenException;
import ro.mathlms.auth.JwtCookieAuthFilter;
import ro.mathlms.auth.JwtCookieSuccessHandler;
import ro.mathlms.auth.PasswordChangeException;
import ro.mathlms.auth.TwoFactorException;
import ro.mathlms.auth.UserNotFoundException;
import ro.mathlms.content.ContentInUseException;
import ro.mathlms.content.ContentNotFoundException;
import ro.mathlms.content.DuplicateContentException;
import ro.mathlms.enchapter.EnChapterConflictException;
import ro.mathlms.enchapter.EnChapterController;
import ro.mathlms.enchapter.EnChapterNotFoundException;
import ro.mathlms.enchapter.EnChapterService;
import ro.mathlms.enchapter.EnChapterSlotService;
import ro.mathlms.gdpr.ErasureNotAllowedException;
import ro.mathlms.gdpr.GdprErasureService;
import ro.mathlms.gdpr.GdprExportRequests;
import ro.mathlms.gdpr.GdprExportUnavailableException;
import ro.mathlms.gdpr.PublicGdprController;
import ro.mathlms.grading.CommentSnippetController;
import ro.mathlms.grading.CommentSnippetService;
import ro.mathlms.grading.SnippetLimitException;
import ro.mathlms.idempotency.IdempotencyConflictException;
import ro.mathlms.idempotency.IdempotencyKeyReusedException;
import ro.mathlms.ingest.AiImportConflictException;
import ro.mathlms.ingest.ChapterSuggestionException;
import ro.mathlms.invite.InviteNotFoundException;
import ro.mathlms.invite.InviteRejectedException;
import ro.mathlms.invite.InviteUnavailableException;
import ro.mathlms.parent.ParentAccessException;
import ro.mathlms.quiz.AttemptExpiredException;
import ro.mathlms.quiz.AttemptLimitException;
import ro.mathlms.quiz.InvalidQuizException;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAccessException;
import ro.mathlms.quiz.QuizInUseException;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.RetakeBlock;
import ro.mathlms.review.ChapterPracticeController;
import ro.mathlms.review.ChapterPracticeService;
import ro.mathlms.review.InvalidReviewException;
import ro.mathlms.student.AdminStudentController;
import ro.mathlms.student.AdminStudentService;
import ro.mathlms.student.StudentNotFoundException;
import ro.mathlms.teaching.TeacherScope;
import ro.mathlms.tutor.StudentTutorController;
import ro.mathlms.tutor.StudentTutorService;
import ro.mathlms.tutor.TutorController;
import ro.mathlms.tutor.TutorException;
import ro.mathlms.tutor.TutorHintService;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Pins what every exception turns into on the wire - status, body text, content type - before the exception handlers
 * are consolidated (backend refactor 2026-11). The frontend shows the error body as text, so a changed word is a
 * changed screen. A test-only controller throws each exception through the real {@code @RestControllerAdvice} beans;
 * the EN chapter pages (whose advice is scoped to {@code EnChapterController}) and the five controllers that answer
 * some exceptions themselves are exercised directly, with their services mocked.
 * When a mapping is changed on purpose, change it here in the same commit.
 */
@WebMvcTest(controllers = {
        ExceptionMappingCharacterizationTest.ThrowingController.class,
        ChapterPracticeController.class,
        EnChapterController.class,
        PublicGdprController.class,
        AdminStudentController.class,
        CommentSnippetController.class,
        TutorController.class,
        StudentTutorController.class})
@Import({ExceptionMappingCharacterizationTest.ThrowingController.class,
        ExceptionMappingCharacterizationTest.MetricsConfig.class})
@WithMockUser(roles = "ADMIN")
class ExceptionMappingCharacterizationTest {

    /** What every error body is sent as (the browser's fetch asks for anything). */
    private static final String TEXT = "text/plain;charset=UTF-8";
    private static final String ACCEPT_ANY = "*/*";
    private static final String UPLOAD_TOO_LARGE =
            "Fișierele trimise sunt prea mari pentru server. Trimite mai puține pagini odată.";

    /** Throws whatever the current test hands it, from an /api/admin route (ADMIN may call it). */
    @RestController
    public static class ThrowingController {

        private volatile Supplier<? extends RuntimeException> next;

        void willThrow(Supplier<? extends RuntimeException> exception) {
            this.next = exception;
        }

        @GetMapping("/api/admin/__characterization/throw")
        public String fail() {
            throw next.get();
        }

        public record Body(@NotBlank String text) {
        }

        @PostMapping("/api/admin/__characterization/validate")
        public String validate(@Valid @RequestBody Body body) {
            return body.text();
        }
    }

    /** A registry the auth handler counts failed logins in, so the side effect can be checked. */
    @TestConfiguration
    static class MetricsConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    /** Its simple name contains "SizeLimitExceeded", like Tomcat's own, which is what the handler looks for. */
    static class FakeSizeLimitExceededException extends RuntimeException {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ThrowingController throwingController;

    @Autowired
    private MeterRegistry meterRegistry;

    // the security configuration's collaborators, as in the other web-slice test
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean
    private CustomOidcUserService customOidcUserService;
    @MockitoBean
    private JwtCookieSuccessHandler jwtCookieSuccessHandler;
    @MockitoBean
    private JwtCookieAuthFilter jwtCookieAuthFilter;

    // the services of the controllers that handle some exceptions themselves
    @MockitoBean
    private ChapterPracticeService chapterPracticeService;
    @MockitoBean
    private GdprErasureService gdprErasureService;
    @MockitoBean
    private GdprExportRequests gdprExportRequests;
    @MockitoBean
    private AdminStudentService adminStudentService;
    @MockitoBean
    private CommentSnippetService commentSnippetService;
    @MockitoBean
    private TutorHintService tutorHintService;
    @MockitoBean
    private StudentTutorService studentTutorService;
    @MockitoBean
    private TeacherScope teacherScope;
    @MockitoBean
    private EnChapterService enChapterService;
    @MockitoBean
    private EnChapterSlotService enChapterSlotService;

    @BeforeEach
    void passThroughAuthFilter() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtCookieAuthFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    // --- exceptions answered by the global advice beans ---

    static Stream<Arguments> mapped() {
        return Stream.of(
                // auth (fixed bodies: the exception's own message must not reach the client)
                mapping("EmailAlreadyRegistered", () -> new EmailAlreadyRegisteredException("ana@scoala.ro"),
                        409, "An account with this email already exists"),
                mapping("BadCredentials", () -> new BadCredentialsException("internal detail"),
                        401, "Invalid email or password"),
                mapping("PasswordChange", () -> new PasswordChangeException("Schimbă parola întâi."),
                        400, "Schimbă parola întâi."),
                mapping("TwoFactor", () -> new TwoFactorException("Codul nu e bun."), 400, "Codul nu e bun."),
                mapping("ErasureNotAllowed", () -> new ErasureNotAllowedException("Contul nu poate fi șters."),
                        409, "Contul nu poate fi șters."),
                mapping("AccountNotActive", AccountNotActiveException::emailNotVerified, 403, "EMAIL_NOT_VERIFIED"),
                mapping("UserNotFound", () -> new UserNotFoundException(5L), 404, "No such account"),
                mapping("Jwt", () -> new JwtException("signature mismatch"), 400, "Invalid or expired token"),
                mapping("InvalidRefreshToken", () -> new InvalidRefreshTokenException("reused"),
                        401, "Invalid or expired refresh token"),
                mapping("IllegalState", () -> new IllegalStateException("AI import 3 is DONE, expected RUNNING"),
                        409, "This action is not valid for the account's current state"),
                mapping("IllegalState caused by an upload size limit",
                        () -> new IllegalStateException("wrapped", new FakeSizeLimitExceededException()),
                        413, UPLOAD_TOO_LARGE),
                mapping("MaxUploadSizeExceeded", () -> new MaxUploadSizeExceededException(1024), 413, UPLOAD_TOO_LARGE),
                // quiz
                mapping("QuizNotFound", () -> new QuizNotFoundException("QuizAttempt", 7L),
                        404, "No QuizAttempt with id 7"),
                mapping("AttemptExpired (409, although its parent is 400)", AttemptExpiredException::new,
                        409, "Timpul pentru acest test a expirat"),
                mapping("AttemptLimit (400 like its parent)", () -> new AttemptLimitException(RetakeBlock.NO_ATTEMPTS_LEFT),
                        400, RetakeBlock.NO_ATTEMPTS_LEFT.message()),
                mapping("QuizInUse", () -> new QuizInUseException("Testul a fost deja dat."), 409, "Testul a fost deja dat."),
                mapping("InvalidQuiz", () -> new InvalidQuizException("Item does not belong to this quiz"),
                        400, "Item does not belong to this quiz"),
                mapping("QuizAccess", () -> new QuizAccessException("Unknown user x@y.ro"), 403, "Unknown user x@y.ro"),
                // content (+ two framework exceptions this advice answers for the whole app)
                mapping("ContentNotFound", () -> new ContentNotFoundException("Book", 3L), 404, "No Book with id 3"),
                mapping("DuplicateContent", () -> new DuplicateContentException("Există deja."), 409, "Există deja."),
                mapping("ContentInUse", () -> new ContentInUseException("Are capitole."), 409, "Are capitole."),
                mapping("ObjectOptimisticLockingFailure", () -> new ObjectOptimisticLockingFailureException(Quiz.class, 1L),
                        409, "This item was changed by someone else. Reload and try again."),
                mapping("DataIntegrityViolation", () -> new DataIntegrityViolationException("duplicate key value"),
                        409, "Elementul e încă folosit sau intră în conflict cu unul existent."),
                // assignment
                mapping("AssignmentNotFound", () -> new AssignmentNotFoundException("Nu există tema 9"),
                        404, "Nu există tema 9"),
                mapping("InvalidAssignment", () -> new InvalidAssignmentException("Termenul a trecut."),
                        400, "Termenul a trecut."),
                mapping("AssignmentConflict", () -> new AssignmentConflictException("Tema există deja."),
                        409, "Tema există deja."),
                // idempotency (fixed Romanian bodies)
                mapping("IdempotencyKeyReused", () -> new IdempotencyKeyReusedException("internal detail"),
                        422, "Cererea a fost deja trimisă cu alt conținut."),
                mapping("IdempotencyConflict", () -> new IdempotencyConflictException("internal detail"),
                        409, "Lucrarea se trimite chiar acum. Reîncearcă peste câteva secunde."),
                // AI import
                mapping("AiImportConflict", () -> new AiImportConflictException("Importul rulează."),
                        409, "Importul rulează."),
                mapping("ChapterSuggestion", () -> new ChapterSuggestionException("AI-ul nu răspunde."),
                        503, "AI-ul nu răspunde."),
                // invites
                mapping("InviteNotFound", () -> new InviteNotFoundException("Nu există invitația."),
                        404, "Nu există invitația."),
                mapping("InviteRejected", () -> new InviteRejectedException("Contul există deja."),
                        409, "Contul există deja."),
                mapping("InviteUnavailable", () -> new InviteUnavailableException("Linkul a expirat."),
                        410, "Linkul a expirat."),
                // parent, review
                mapping("ParentAccess", () -> new ParentAccessException("Nu e copilul tău."), 403, "Nu e copilul tău."),
                mapping("InvalidReview", () -> new InvalidReviewException("Întrebarea nu e în recapitulare."),
                        409, "Întrebarea nu e în recapitulare."));
    }

    private static Arguments mapping(String name, Supplier<? extends RuntimeException> exception, int status,
                                     String body) {
        return Arguments.of(name, exception, status, body);
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("mapped")
    void eachExceptionKeepsItsStatusBodyAndContentType(String name, Supplier<? extends RuntimeException> exception,
                                                       int status, String body) throws Exception {
        MvcResult result = perform(exception);

        assertThat(result.getResponse().getStatus()).as("status of %s", name).isEqualTo(status);
        assertThat(result.getResponse().getContentAsString()).as("body of %s", name).isEqualTo(body);
        assertThat(result.getResponse().getContentType()).as("content type of %s", name).isEqualTo(TEXT);
    }

    @Test
    void badCredentialsAreCountedForTheBruteForceAlert() throws Exception {
        double before = meterRegistry.get("security_failed_logins").counter().count();

        perform(() -> new BadCredentialsException("x"));

        assertThat(meterRegistry.get("security_failed_logins").counter().count()).isEqualTo(before + 1);
    }

    @Test
    void aResponseStatusExceptionIsAnsweredBySpringItself() throws Exception {
        MvcResult result = perform(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void aBodyThatFailsValidationIsAnsweredBySpringItself() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/__characterization/validate").with(csrf())
                        .accept(ACCEPT_ANY).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\" \"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    // --- exceptions no global advice answers: outside their own controller they reach the container (a 500) ---

    static Stream<Arguments> unmapped() {
        return Stream.of(
                // EnChapterExceptionHandler is scoped to EnChapterController (assignableTypes), see below
                Arguments.of("EnChapterNotFound", (Supplier<RuntimeException>) () -> new EnChapterNotFoundException("EnChapter", 4L)),
                Arguments.of("EnChapterConflict", (Supplier<RuntimeException>) () -> new EnChapterConflictException("x")),
                Arguments.of("IllegalArgument", (Supplier<RuntimeException>) () -> new IllegalArgumentException("x")),
                Arguments.of("StudentNotFound", (Supplier<RuntimeException>) () -> new StudentNotFoundException(1L)),
                Arguments.of("Tutor", (Supplier<RuntimeException>) () -> new TutorException("x")),
                Arguments.of("SnippetLimit", (Supplier<RuntimeException>) () -> new SnippetLimitException("x")),
                Arguments.of("GdprExportUnavailable", (Supplier<RuntimeException>) () -> new GdprExportUnavailableException("x")),
                Arguments.of("EmailNotAllowed", (Supplier<RuntimeException>) () -> new EmailNotAllowedException("x@y.ro")),
                Arguments.of("any other RuntimeException", (Supplier<RuntimeException>) () -> new RuntimeException("x")));
    }

    @ParameterizedTest(name = "{0} is not handled globally")
    @MethodSource("unmapped")
    void exceptionsWithoutAGlobalHandlerPropagate(String name, Supplier<? extends RuntimeException> exception) {
        RuntimeException thrown = exception.get();
        assertThatThrownBy(() -> perform(() -> thrown)).as(name).rootCause().isSameAs(thrown);
    }

    // --- the EN chapter pages: an advice scoped to EnChapterController only ---

    static Stream<Arguments> enChapterPages() {
        return Stream.of(
                mapping("EnChapterNotFound", () -> new EnChapterNotFoundException("EnChapter", 4L),
                        404, "No EnChapter with id 4"),
                mapping("EnChapterConflict", () -> new EnChapterConflictException("Codul e folosit."),
                        409, "Codul e folosit."),
                mapping("IllegalArgument", () -> new IllegalArgumentException("Codul trebuie să fie A1-A15 sau G1-G10."),
                        400, "Codul trebuie să fie A1-A15 sau G1-G10."));
    }

    @ParameterizedTest(name = "chapter page: {0} -> {2}")
    @MethodSource("enChapterPages")
    void theChapterPagesMapTheirOwnExceptions(String name, Supplier<? extends RuntimeException> exception, int status,
                                              String body) throws Exception {
        when(enChapterService.page(4L)).thenThrow(exception.get());

        MvcResult result = mockMvc.perform(get("/api/en-chapters/4").accept(ACCEPT_ANY)).andReturn();

        assertResponse(result, status, body);
    }

    // --- the controllers that answer an exception themselves ---

    @Test
    @WithMockUser(authorities = {"ROLE_STUDENT", "STATUS_ACTIVE"})
    void chapterPracticeAnswersAMissingChapterInItsOwnWords() throws Exception {
        when(chapterPracticeService.practice(anyString(), eq(1L))).thenThrow(new EnChapterNotFoundException("EnChapter", 1L));

        MvcResult result = mockMvc.perform(get("/api/quiz/en-chapters/1/practice").accept(ACCEPT_ANY)).andReturn();

        assertResponse(result, 404, "Capitolul nu mai există.");
    }

    @Test
    void gdprDownloadAnswersAnExpiredLinkWithGone() throws Exception {
        when(gdprExportRequests.download("expired")).thenThrow(new GdprExportUnavailableException("Linkul a expirat."));

        MvcResult result = mockMvc.perform(get("/api/public/gdpr/export/download").param("token", "expired")
                .accept(ACCEPT_ANY)).andReturn();

        assertResponse(result, 410, "Linkul a expirat.");
    }

    @Test
    void adminStudentsAnswerAMissingStudentWithNotFound() throws Exception {
        when(adminStudentService.profile(1L)).thenThrow(new StudentNotFoundException(1L));

        MvcResult result = mockMvc.perform(get("/api/admin/students/1").accept(ACCEPT_ANY)).andReturn();

        assertResponse(result, 404, "Nu există elevul 1.");
    }

    @Test
    void commentSnippetsAnswerTheLimitWithConflict() throws Exception {
        when(commentSnippetService.create(anyString(), eq("Bine"))).thenThrow(new SnippetLimitException("Ai deja 50."));

        MvcResult result = mockMvc.perform(post("/api/admin/comment-snippets").with(csrf()).accept(ACCEPT_ANY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Bine\"}")).andReturn();

        assertResponse(result, 409, "Ai deja 50.");
    }

    @Test
    void tutorAnswersAnUnreachableTutorWithServiceUnavailable() throws Exception {
        when(tutorHintService.suggest(1L)).thenThrow(new TutorException("Tutorul nu răspunde."));

        MvcResult result = mockMvc.perform(post("/api/admin/quiz-items/1/tutor-hints").with(csrf())
                .accept(ACCEPT_ANY)).andReturn();

        assertResponse(result, 503, "Tutorul nu răspunde.");
    }

    @ParameterizedTest
    @MethodSource("studentTutorRefusals")
    @WithMockUser(authorities = {"ROLE_STUDENT", "STATUS_ACTIVE"})
    void theStudentsTutorBoxAnswersARefusalWithItsOwnStatusAndSentence(RuntimeException refusal, int status)
            throws Exception {
        when(studentTutorService.explain(any(), any(), any())).thenThrow(refusal);

        MvcResult result = mockMvc.perform(post("/api/quiz/tutor/explain").with(csrf()).accept(ACCEPT_ANY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"ce e modulul?\",\"history\":[]}"))
                .andReturn();

        assertResponse(result, status, refusal.getMessage());
    }

    static Stream<Arguments> studentTutorRefusals() {
        return Stream.of(
                Arguments.of(new StudentTutorService.ClosedException(HttpStatus.CONFLICT, "Test deschis."), 409),
                Arguments.of(new StudentTutorService.ClosedException(HttpStatus.TOO_MANY_REQUESTS, "Gata pe azi."), 429),
                Arguments.of(new TutorException("Tutorul nu răspunde."), 503));
    }

    private MvcResult perform(Supplier<? extends RuntimeException> exception) throws Exception {
        throwingController.willThrow(exception);
        return mockMvc.perform(get("/api/admin/__characterization/throw").accept(ACCEPT_ANY)).andReturn();
    }

    private static void assertResponse(MvcResult result, int status, String body) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentAsString()).isEqualTo(body);
        assertThat(result.getResponse().getContentType()).isEqualTo(TEXT);
    }
}
