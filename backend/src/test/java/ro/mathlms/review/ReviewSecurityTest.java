package ro.mathlms.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.review.ReviewDtos.ReviewTodayDto;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The review queue is the student's own; the teacher reads a class; parents go through /api/parent. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReviewSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService service;

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void aStudentReadsAndAnswersTheirOwnQueue() throws Exception {
        when(service.today("ana@scoala.ro")).thenReturn(new ReviewTodayDto(10, 0, 0, 0, 0, 0, List.of()));

        mockMvc.perform(get("/api/quiz/review/today")).andExpect(status().isOk());
        mockMvc.perform(post("/api/quiz/review/items/7/answer").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"optionId\":70}"))
                .andExpect(status().isOk());
        verify(service).answer("ana@scoala.ro", 7L, 70L);
    }

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void aQuestionNotDueTodayIsAConflictWithTheReason() throws Exception {
        doThrow(new InvalidReviewException("Întrebarea nu e de recapitulat azi."))
                .when(service).answer(anyString(), anyLong(), anyLong());

        mockMvc.perform(post("/api/quiz/review/items/7/answer").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"optionId\":70}"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Întrebarea nu e de recapitulat azi."));
    }

    @Test
    @WithMockUser(username = "maria@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_PARENT"})
    void aParentCannotUseTheStudentsQueue() throws Exception {
        mockMvc.perform(get("/api/quiz/review/today")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"STATUS_ACTIVE", "ROLE_ADMIN"})
    void theTeacherReadsAClass() throws Exception {
        when(service.forClass(5L)).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/classes/5/review")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void aStudentCannotReadAClass() throws Exception {
        mockMvc.perform(get("/api/admin/classes/5/review")).andExpect(status().isForbidden());
    }
}
