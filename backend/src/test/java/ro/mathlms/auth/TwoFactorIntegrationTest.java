package ro.mathlms.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F8 end to end on real Postgres + Redis, with the admin requirement on: an ADMIN without it is kept out of the staff
 * pages until they enrol; a sign-in with it stops after the password (no session) until the app's code - each code
 * once, a wrong code refused, five wrong tries end the attempt; a recovery code works exactly once.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.auth.two-factor.required-for-admin=true")
@AutoConfigureMockMvc
class TwoFactorIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = active("director", Role.ADMIN);
    }

    private User active(String who, Role role) {
        User user = User.registerLocal(who + "." + System.nanoTime() + "@scoala.ro", "Nume " + who,
                passwordEncoder.encode("parola-buna-123"), role);
        user.verifyEmail();
        user.approve(role);
        return userRepository.save(user);
    }

    private static RequestPostProcessor as(User account, String role) {
        return user(account.getEmail()).authorities(new SimpleGrantedAuthority("ROLE_" + role),
                new SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    private static String code(String secret, long stepOffset) {
        return Totp.code(Totp.unbase32(secret), Totp.stepOf(Instant.now()) + stepOffset);
    }

    /** Enrols {@code account} through the API; returns the secret and the recovery codes. */
    private Enrolled enrol(User account, String role) throws Exception {
        String setup = mockMvc.perform(post("/api/me/2fa/setup").with(as(account, role)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/Math%20LMS")))
                .andReturn().getResponse().getContentAsString();
        String secret = setup.replaceAll(".*\"secret\":\"([A-Z2-7]+)\".*", "$1");
        String codes = mockMvc.perform(post("/api/me/2fa/enable").with(as(account, role)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code(secret, 0) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        String firstRecovery = codes.replaceAll(".*\"recoveryCodes\":\\[\"([a-z0-9-]+)\".*", "$1");
        return new Enrolled(secret, firstRecovery);
    }

    private record Enrolled(String secret, String recoveryCode) {
    }

    private MvcResult login(User account) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.getEmail() + "\",\"password\":\"parola-buna-123\"}"))
                .andReturn();
    }

    @Test
    void anAdminWithoutItIsKeptOutOfTheStaffPagesUntilTheyEnrol() throws Exception {
        mockMvc.perform(get("/api/admin/quizzes").with(as(admin, "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(TwoFactorEnrolmentGate.SETUP_REQUIRED));
        mockMvc.perform(get("/api/auth/me").with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.twoFactorSetupRequired").value(true));

        enrol(admin, "ADMIN");

        mockMvc.perform(get("/api/admin/quizzes").with(as(admin, "ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.twoFactorEnabled").value(true))
                .andExpect(jsonPath("$.twoFactorSetupRequired").value(false));
        // and it cannot be switched off again
        mockMvc.perform(post("/api/me/2fa/disable").with(as(admin, "ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPasswordAloneGivesNoSessionTheCodeDoesAndOnlyOnce() throws Exception {
        Enrolled enrolled = enrol(admin, "ADMIN");

        MvcResult first = login(admin);
        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        assertThat(first.getResponse().getCookie(JwtCookieSuccessHandler.COOKIE_NAME)).isNull(); // no session yet
        Cookie challenge = first.getResponse().getCookie(MfaChallenges.COOKIE);
        assertThat(challenge).isNotNull();
        assertThat(challenge.isHttpOnly()).isTrue();

        mockMvc.perform(post("/api/auth/2fa/verify").cookie(challenge).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
        String code = code(enrolled.secret(), 1); // the enrolment used the current step
        MvcResult done = mockMvc.perform(post("/api/auth/2fa/verify").cookie(challenge).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(done.getResponse().getCookie(JwtCookieSuccessHandler.COOKIE_NAME)).isNotNull();

        // the same code a second time (a new sign-in) is refused: each code works once
        Cookie again = login(admin).getResponse().getCookie(MfaChallenges.COOKIE);
        mockMvc.perform(post("/api/auth/2fa/verify").cookie(again).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest());
        // a recovery code works - once
        mockMvc.perform(post("/api/auth/2fa/verify").cookie(again).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + enrolled.recoveryCode() + "\"}"))
                .andExpect(status().isOk());
        Cookie third = login(admin).getResponse().getCookie(MfaChallenges.COOKIE);
        mockMvc.perform(post("/api/auth/2fa/verify").cookie(third).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + enrolled.recoveryCode() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fiveWrongTriesEndTheSignIn() throws Exception {
        Enrolled enrolled = enrol(admin, "ADMIN");
        Cookie challenge = login(admin).getResponse().getCookie(MfaChallenges.COOKIE);

        for (int i = 0; i < MfaChallenges.MAX_ATTEMPTS; i++) {
            mockMvc.perform(post("/api/auth/2fa/verify").cookie(challenge).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"));
        }
        mockMvc.perform(post("/api/auth/2fa/verify").cookie(challenge).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code(enrolled.secret(), 1) + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTeacherMayTurnItOffWithACurrentCodeButAStudentCannotHaveIt() throws Exception {
        User teacher = active("profa", Role.TEACHER);
        // a teacher is not forced: the staff pages are open without it
        mockMvc.perform(get("/api/admin/quizzes").with(as(teacher, "TEACHER"))).andExpect(status().isOk());
        Enrolled enrolled = enrol(teacher, "TEACHER");

        mockMvc.perform(post("/api/me/2fa/disable").with(as(teacher, "TEACHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code(enrolled.secret(), 1) + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(teacher.getId()).orElseThrow().isTwoFactorEnabled()).isFalse();

        User student = active("elev", Role.STUDENT);
        mockMvc.perform(post("/api/me/2fa/setup").with(as(student, "STUDENT")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void theSecretIsStoredEncrypted() throws Exception {
        Enrolled enrolled = enrol(admin, "ADMIN");
        String stored = userRepository.findById(admin.getId()).orElseThrow().getTotpSecret();
        assertThat(stored).isNotEqualTo(enrolled.secret()).doesNotContain(enrolled.secret());
        assertThat(List.of(stored)).isNotEmpty();
    }
}
