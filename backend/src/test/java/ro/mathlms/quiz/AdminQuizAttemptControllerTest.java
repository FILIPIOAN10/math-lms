package ro.mathlms.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.auth.CustomOidcUserService;
import ro.mathlms.auth.JwtCookieAuthFilter;
import ro.mathlms.auth.JwtCookieSuccessHandler;
import ro.mathlms.quiz.AdminAttemptDtos.AdminAttemptDetailDto;
import ro.mathlms.quiz.AdminAttemptDtos.AdminAttemptSummaryDto;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminQuizAttemptController.class)
@Import(AdminQuizAttemptControllerTest.MethodSecurityConfig.class)
class AdminQuizAttemptControllerTest {

    /** Turn on @PreAuthorize for this slice test. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private QuizAttemptService service;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private CustomOidcUserService customOidcUserService;

    @MockitoBean
    private JwtCookieSuccessHandler jwtCookieSuccessHandler;

    @MockitoBean
    private JwtCookieAuthFilter jwtCookieAuthFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtCookieAuthFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPhoto_ReturnsResource() throws Exception {
        Resource mockResource = new ByteArrayResource("dummy image content".getBytes()) {
            @Override
            public String getFilename() {
                return "photo.png";
            }
        };
        when(service.getOpenPhotoResource(1L, 2L)).thenReturn(mockResource);

        mockMvc.perform(get("/api/admin/quiz/attempts/1/responses/2/photo"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"photo.png\""))
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void gradeItem_Success() throws Exception {
        GradeRequestDto request = new GradeRequestDto(15, "Bine!");

        mockMvc.perform(put("/api/admin/quiz/attempts/1/responses/2/grade")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(service).gradeOpenResponse(1L, 2L, 15, "Bine!");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void gradeItem_FailsWhenPointsNegative() throws Exception {
        GradeRequestDto request = new GradeRequestDto(-5, null);

        mockMvc.perform(put("/api/admin/quiz/attempts/1/responses/2/grade")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void gradeItem_FailsForStudentRole() throws Exception {
        GradeRequestDto request = new GradeRequestDto(10, null);

        mockMvc.perform(put("/api/admin/quiz/attempts/1/responses/2/grade")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void finalizeGrading_Success() throws Exception {
        mockMvc.perform(post("/api/admin/quiz/attempts/1/mark-graded")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());

        verify(service).finalizeGrading(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listDefaultsToSubmittedQueue() throws Exception {
        when(service.listForGrading(QuizAttemptStatus.SUBMITTED)).thenReturn(List.of(
                new AdminAttemptSummaryDto(1L, 10L, "Simulare EN", 5L, "Ana", QuizAttemptStatus.SUBMITTED, null, null, 35)));

        mockMvc.perform(get("/api/admin/quiz/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("Ana"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listAcceptsAStatusFilter() throws Exception {
        when(service.listForGrading(QuizAttemptStatus.GRADED)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/quiz/attempts").param("status", "GRADED"))
                .andExpect(status().isOk());

        verify(service).listForGrading(QuizAttemptStatus.GRADED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void detailReturnsTheAttempt() throws Exception {
        when(service.getAttemptForGrading(1L)).thenReturn(new AdminAttemptDetailDto(
                1L, "Simulare EN", "Ana", QuizAttemptStatus.SUBMITTED, null, null, 35, List.of(), null));

        mockMvc.perform(get("/api/admin/quiz/attempts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(35));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotSeeTheGradingQueue() throws Exception {
        mockMvc.perform(get("/api/admin/quiz/attempts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void gradeItem_RejectsACommentLongerThan2000Characters() throws Exception {
        GradeRequestDto request = new GradeRequestDto(5, "x".repeat(2001));

        mockMvc.perform(put("/api/admin/quiz/attempts/1/responses/2/grade")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void commentOnTheWholePaper() throws Exception {
        mockMvc.perform(put("/api/admin/quiz/attempts/1/comment")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Felicitări!\"}"))
                .andExpect(status().isNoContent());

        verify(service).commentOnAttempt(1L, "Felicitări!");
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aStudentCannotCommentOnAPaper() throws Exception {
        mockMvc.perform(put("/api/admin/quiz/attempts/1/comment")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"10 pe linie\"}"))
                .andExpect(status().isForbidden());
    }
}
