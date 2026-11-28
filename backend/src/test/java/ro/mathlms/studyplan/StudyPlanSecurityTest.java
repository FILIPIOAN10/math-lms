package ro.mathlms.studyplan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may read and change which plan: the student their own (and their target), the parent only their own child's and
 * only to read, the teacher any of their students' (pins included); only an admin sets the exam date.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StudyPlanSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyPlanService service;

    @MockitoBean
    private EnGoalService goalService;

    @Test
    @WithMockUser(username = "maria@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_PARENT"})
    void aParentCannotUseTheStudentsEndpoints() throws Exception {
        mockMvc.perform(get("/api/quiz/study-plan")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/quiz/en-goal").with(csrf()).contentType("application/json").content("{\"targetGrade\":9}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "maria@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_PARENT"})
    void aParentWhoIsNotTheChildsParentIsRefused() throws Exception {
        // No such parent account, and certainly not this child's: the parent API's uniform 403.
        mockMvc.perform(get("/api/parent/children/5/study-plan")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parent/children/5/en-goal")).andExpect(status().isForbidden());
        verify(service, never()).planFor(any());
    }

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void aStudentCannotCallTheTeachersEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/students/5/study-plan")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/students/5/en-goal")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/students/5/study-plan/pins/3").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/students/5/study-plan/pins/3").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/classes/5/en-goals")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/en-exam-date")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/en-exam-date").with(csrf()).contentType("application/json")
                .content("{\"examDate\":\"2027-06-17\"}")).andExpect(status().isForbidden());
        verify(goalService, never()).setExamDate(any());
    }

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void aStudentCannotReadAnotherChildsPlanThroughTheParentApi() throws Exception {
        mockMvc.perform(get("/api/parent/children/5/study-plan")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "prof@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_TEACHER"})
    void aTeacherReadsTheExamDateButOnlyAnAdminSetsIt() throws Exception {
        mockMvc.perform(get("/api/admin/en-exam-date")).andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/en-exam-date").with(csrf()).contentType("application/json")
                .content("{\"examDate\":\"2027-06-17\"}")).andExpect(status().isForbidden());
        verify(goalService, never()).setExamDate(any());
    }

    @Test
    @WithMockUser(username = "prof@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_TEACHER"})
    void aTeacherOnlyReachesTheirOwnStudents() throws Exception {
        // Student 987654321 is in none of this teacher's classes (nor anywhere): the teacher scope's 404.
        mockMvc.perform(get("/api/admin/students/987654321/study-plan")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/students/987654321/study-plan/pins/3").with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/classes/987654321/en-goals")).andExpect(status().isNotFound());
    }
}
