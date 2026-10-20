package ro.mathlms.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T5 on real Postgres: one student's page for the teacher, and nobody else may read it. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminStudentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    private User ana;
    private User maria;
    private SchoolClass clasa;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        maria = userRepository.save(new User("maria." + stamp + "@scoala.ro", "Maria Pop", Role.PARENT));
        User student = new User("ana." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT);
        student.linkParent(maria);
        ana = userRepository.save(student);
        clasa = schoolClassRepository.save(new SchoolClass("Clasa a 8-a " + stamp, null));
        enrollmentRepository.save(new Enrollment(ana, clasa));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theProfileNamesTheClassesAndTheParent() throws Exception {
        mockMvc.perform(get("/api/admin/students/" + ana.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Pop"))
                .andExpect(jsonPath("$.classes", hasSize(1)))
                .andExpect(jsonPath("$.classes[0].name").value(clasa.getName()))
                .andExpect(jsonPath("$.parentId").value(maria.getId()))
                .andExpect(jsonPath("$.parentName").value("Maria Pop"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void everySectionAnswersForAStudentWithNothingDoneYet() throws Exception {
        String base = "/api/admin/students/" + ana.getId();
        mockMvc.perform(get(base + "/attempts")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get(base + "/assignments")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get(base + "/progress")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get(base + "/mastery")).andExpect(status().isOk());
        mockMvc.perform(get(base + "/review")).andExpect(status().isOk()).andExpect(jsonPath("$.streak").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anIdThatIsNotAStudentIsA404() throws Exception {
        mockMvc.perform(get("/api/admin/students/" + maria.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/students/" + maria.getId() + "/attempts")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/students/-1")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "PARENT")
    void aParentCannotUseTheTeachersView() throws Exception {
        mockMvc.perform(get("/api/admin/students/" + ana.getId())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aStudentCannotReadAnotherStudentsPage() throws Exception {
        mockMvc.perform(get("/api/admin/students/" + ana.getId() + "/mastery")).andExpect(status().isForbidden());
    }
}
