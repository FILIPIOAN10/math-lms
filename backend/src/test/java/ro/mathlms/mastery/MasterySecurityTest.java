package ro.mathlms.mastery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.mastery.MasteryDtos.ClassMasteryDto;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may read which mastery map: a student their own, the teacher any class; the parent's goes through /api/parent. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MasterySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MasteryService service;

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void aStudentReadsTheirOwnMap() throws Exception {
        when(service.forStudent("ana@scoala.ro")).thenReturn(List.of());

        mockMvc.perform(get("/api/quiz/mastery")).andExpect(status().isOk());
        verify(service).forStudent("ana@scoala.ro");
    }

    @Test
    @WithMockUser(username = "maria@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_PARENT"})
    void aParentCannotUseTheStudentsEndpoint() throws Exception {
        mockMvc.perform(get("/api/quiz/mastery")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"STATUS_ACTIVE", "ROLE_ADMIN"})
    void theTeacherReadsAClassGridAndTheChapterList() throws Exception {
        when(service.forClass(5L)).thenReturn(new ClassMasteryDto(List.of(), List.of()));

        mockMvc.perform(get("/api/admin/classes/5/mastery")).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/chapters")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void aStudentCannotReadAClassGrid() throws Exception {
        mockMvc.perform(get("/api/admin/classes/5/mastery")).andExpect(status().isForbidden());
    }
}
