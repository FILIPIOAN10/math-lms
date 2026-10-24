package ro.mathlms.gdpr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.auth.EmailService;
import ro.mathlms.auth.TokenPurpose;
import ro.mathlms.auth.VerificationTokenService;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GDPR Art. 15 deferred, on real Postgres + Redis: asking queues the build and returns at once (the link is never in
 * the response); the worker builds the ZIP and emails a single-use link; the link streams it once; asking again while
 * it is built is answered, not queued; erasure takes the archives along.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class GdprExportFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private GdprExportRepository exportRepository;
    @Autowired private GdprExportRequests requests;
    @Autowired private GdprErasureService erasureService;
    @Autowired private VerificationTokenService tokenService;
    @MockitoBean private EmailService emailService;

    private User student;

    @BeforeEach
    void setUp() {
        student = User.registerLocal("export." + System.nanoTime() + "@scoala.ro", "Elev Export", "HASH", Role.STUDENT);
        student.verifyEmail();
        student.approve(Role.STUDENT);
        student = userRepository.save(student);
    }

    private RequestPostProcessor asStudent() {
        return user(student.getEmail()).authorities(
                new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    private String emailedToken() {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce())
                .sendGdprExportReadyEmail(eq(student.getEmail()), token.capture(), anyLong());
        return token.getValue();
    }

    @Test
    void askingQueuesTheBuildAndTheEmailedLinkDownloadsItOnce() throws Exception {
        mockMvc.perform(post("/api/users/gdpr/export").with(asStudent()).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("QUEUED"));
        // asking again while it is being built: answered, not queued twice
        mockMvc.perform(post("/api/users/gdpr/export").with(asStudent()).with(csrf()))
                .andExpect(jsonPath("$.outcome").value("ALREADY_BUILDING"));
        verify(emailService, never()).sendGdprExportReadyEmail(anyString(), anyString(), anyLong());

        GdprExport pending = exportRepository.findLive(student.getId()).orElseThrow();
        requests.build(pending.getId()); // what the outbox worker does

        byte[] zip = mockMvc.perform(get("/api/public/gdpr/export/download").param("token", emailedToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip))) {
            assertThat(entries.getNextEntry()).isNotNull();
        }
        // the same link a second time: gone, with a sentence the person can act on
        mockMvc.perform(get("/api/public/gdpr/export/download").param("token", emailedToken()))
                .andExpect(status().isGone());
        assertThat(exportRepository.findLive(student.getId()).orElseThrow().getDownloadedAt()).isNotNull();

        // once built, asking again emails a fresh link to the same archive
        mockMvc.perform(post("/api/users/gdpr/export").with(asStudent()).with(csrf()))
                .andExpect(jsonPath("$.outcome").value("LINK_RESENT"));
    }

    @Test
    void aForgedLinkIsRefused() throws Exception {
        mockMvc.perform(get("/api/public/gdpr/export/download").param("token", "a.b.c")).andExpect(status().isGone());
    }

    @Test
    void erasureTakesTheArchivesAlong() {
        requests.request(student.getEmail());
        requests.build(exportRepository.findLive(student.getId()).orElseThrow().getId());

        erasureService.confirmErasure(tokenService.generate(student.getEmail(), TokenPurpose.ERASE_ACCOUNT));

        assertThat(exportRepository.findLive(student.getId())).isEmpty();
    }
}
