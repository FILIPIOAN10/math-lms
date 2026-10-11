package ro.mathlms.invite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.auth.EmailService;
import ro.mathlms.auth.InviteTokenService;
import ro.mathlms.auth.TokenPurpose;
import ro.mathlms.auth.VerificationTokenService;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.invite.InviteLinkDtos.InviteLinkDto;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T1 end to end on real Postgres, through the real auth endpoints: a newcomer who joins through a class or parent link
 * is active, enrolled / linked as soon as the email is confirmed — no approval, enrolment or linking by the teacher —
 * while the cap, expiry and revocation keep a forwarded link from letting strangers in.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InviteLinkIntegrationTest {

    @MockitoBean private EmailService emailService;

    @Autowired private MockMvc mockMvc;
    @Autowired private InviteLinkService inviteLinkService;
    @Autowired private InviteLinkRepository linkRepository;
    @Autowired private InviteTokenService inviteTokenService;
    @Autowired private VerificationTokenService verificationTokenService;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private UserRepository userRepository;

    private String stamp;
    private SchoolClass schoolClass;

    @BeforeEach
    void setUp() {
        stamp = String.valueOf(System.nanoTime());
        schoolClass = schoolClassRepository.save(new SchoolClass("Clasa a 8-a " + stamp, null));
    }

    private String email(String who) {
        return who + "." + stamp + "@scoala.ro";
    }

    private static String code(InviteLinkDto link) {
        return link.url().substring(link.url().indexOf("token=") + "token=".length());
    }

    private ResultActions register(String email, String token) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"fullName\":\"Nume Prenume\",\"password\":\"parola123\","
                        + "\"inviteToken\":\"" + token + "\"}"));
    }

    private ResultActions confirmEmail(String email) throws Exception {
        String token = verificationTokenService.generate(email, TokenPurpose.VERIFY_EMAIL);
        return mockMvc.perform(get("/api/auth/verify-email").param("token", token));
    }

    private User account(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private int usesOf(InviteLinkDto link) {
        return linkRepository.findById(link.id()).orElseThrow().getUses();
    }

    private User activeStudent(String who) {
        User student = User.registerLocal(email(who), "Ana Pop", "HASH", Role.STUDENT);
        student.verifyEmail();
        student.approve(Role.STUDENT);
        return userRepository.save(student);
    }

    // --- class link ---

    @Test
    void aStudentWhoJoinsThroughTheClassLinkIsActiveAndEnrolledOnceTheEmailIsConfirmed() throws Exception {
        InviteLinkDto link = inviteLinkService.createClassInvite(schoolClass.getId(), null);
        assertThat(link.maxUses()).isEqualTo(InviteLink.DEFAULT_CLASS_USES);

        register(email("ana"), code(link)).andExpect(status().isCreated());
        assertThat(account(email("ana")).getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(usesOf(link)).isEqualTo(1);

        confirmEmail(email("ana")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        User ana = account(email("ana"));
        assertThat(ana.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(ana.getRole()).isEqualTo(Role.STUDENT);
        assertThat(enrollmentRepository.existsByStudentIdAndSchoolClassId(ana.getId(), schoolClass.getId())).isTrue();
    }

    @Test
    void theCapStopsTheNextNewcomer() throws Exception {
        InviteLinkDto link = inviteLinkService.createClassInvite(schoolClass.getId(), 1);

        register(email("ana"), code(link)).andExpect(status().isCreated());
        register(email("strain"), code(link))
                .andExpect(status().isGone())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("numărul maxim")));

        assertThat(userRepository.findByEmail(email("strain"))).isEmpty();
        assertThat(usesOf(link)).isEqualTo(1);
    }

    @Test
    void aTakenEmailGivesTheUseBack() throws Exception {
        InviteLinkDto link = inviteLinkService.createClassInvite(schoolClass.getId(), 1);
        activeStudent("ana");

        register(email("ana"), code(link)).andExpect(status().isConflict());
        assertThat(usesOf(link)).isZero();

        register(email("radu"), code(link)).andExpect(status().isCreated());
    }

    @Test
    void aRevokedLinkTurnsNewcomersAwayButWhoeverRegisteredBeforeStillJoins() throws Exception {
        InviteLinkDto link = inviteLinkService.createClassInvite(schoolClass.getId(), null);
        register(email("ana"), code(link)).andExpect(status().isCreated());

        inviteLinkService.revoke(link.id());

        register(email("strain"), code(link))
                .andExpect(status().isGone())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("oprit")));
        assertThat(inviteLinkService.activeClassInvites(schoolClass.getId())).isEmpty();

        confirmEmail(email("ana")).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(enrollmentRepository.existsByStudentIdAndSchoolClassId(account(email("ana")).getId(),
                schoolClass.getId())).isTrue();
    }

    @Test
    void anExpiredLinkIsRefused() throws Exception {
        InviteLinkDto link = inviteLinkService.createClassInvite(schoolClass.getId(), null);
        InviteLink stored = linkRepository.findById(link.id()).orElseThrow();
        ReflectionTestUtils.setField(stored, "expiresAt", Instant.now().minusSeconds(60));
        linkRepository.save(stored);

        register(email("ana"), code(link))
                .andExpect(status().isGone())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("expirat")));
        assertThat(inviteLinkService.activeClassInvites(schoolClass.getId())).isEmpty();
    }

    @Test
    void anAccountWhoseClassWasDeletedMeanwhileWaitsForApprovalInstead() throws Exception {
        InviteLinkDto link = inviteLinkService.createClassInvite(schoolClass.getId(), null);
        register(email("ana"), code(link)).andExpect(status().isCreated());

        schoolClassRepository.deleteById(schoolClass.getId()); // the link goes with it (ON DELETE CASCADE)

        confirmEmail(email("ana")).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        assertThat(account(email("ana")).getInviteLinkId()).isNull();
    }

    // --- parent link ---

    @Test
    void aParentWhoJoinsThroughTheStudentsLinkIsActiveAndLinkedToThatStudent() throws Exception {
        User ana = activeStudent("ana");
        InviteLinkDto link = inviteLinkService.createParentInvite(ana.getId());
        assertThat(link.maxUses()).isEqualTo(1);
        assertThat(link.studentName()).isEqualTo("Ana Pop");

        register(email("maria"), code(link)).andExpect(status().isCreated());
        confirmEmail(email("maria")).andExpect(jsonPath("$.status").value("ACTIVE"));

        User maria = account(email("maria"));
        assertThat(maria.getRole()).isEqualTo(Role.PARENT);
        assertThat(userRepository.findByIdAndParentId(ana.getId(), maria.getId())).isPresent();

        // Single use, and the student now has a parent: no second link.
        register(email("al-doilea"), code(link)).andExpect(status().isGone());
        assertThatThrownBy(() -> inviteLinkService.createParentInvite(ana.getId()))
                .isInstanceOf(InviteRejectedException.class)
                .hasMessageContaining("are deja un părinte");
    }

    @Test
    void aParentWhoseChildErasedTheAccountMeanwhileWaitsForApproval() throws Exception {
        User ana = activeStudent("ana");
        InviteLinkDto link = inviteLinkService.createParentInvite(ana.getId());
        register(email("maria"), code(link)).andExpect(status().isCreated());

        User erased = userRepository.findById(ana.getId()).orElseThrow();
        erased.erase("erased-" + stamp + "@erased.invalid", "Cont șters");
        userRepository.save(erased);

        confirmEmail(email("maria")).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        assertThat(userRepository.findByIdAndParentId(ana.getId(), account(email("maria")).getId())).isEmpty();
    }

    @Test
    void aNewParentLinkReplacesTheUnusedOne() throws Exception {
        User ana = activeStudent("ana");
        InviteLinkDto first = inviteLinkService.createParentInvite(ana.getId());
        InviteLinkDto second = inviteLinkService.createParentInvite(ana.getId());

        mockMvc.perform(get("/api/public/invites/preview").param("token", code(first))).andExpect(status().isGone());
        mockMvc.perform(get("/api/public/invites/preview").param("token", code(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PARENT"))
                .andExpect(jsonPath("$.direct").value(true));
    }

    @Test
    void onlyAnActiveStudentGetsAParentLink() {
        User pending = userRepository.save(User.registerLocal(email("nou"), "Radu Nou", "HASH", Role.STUDENT));

        assertThatThrownBy(() -> inviteLinkService.createParentInvite(pending.getId()))
                .isInstanceOf(InviteRejectedException.class);
        assertThatThrownBy(() -> inviteLinkService.createParentInvite(-1L))
                .isInstanceOf(InviteNotFoundException.class);
    }

    // --- preview + the older role invites ---

    @Test
    void thePreviewNamesTheClassAndUsesNothingUp() throws Exception {
        InviteLinkDto link = inviteLinkService.createClassInvite(schoolClass.getId(), 1);

        mockMvc.perform(get("/api/public/invites/preview").param("token", code(link)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.className").value(schoolClass.getName()))
                .andExpect(jsonPath("$.direct").value(true));
        assertThat(usesOf(link)).isZero();

        mockMvc.perform(get("/api/public/invites/preview").param("token", "NuExistaAsaCevaInBaza1234"))
                .andExpect(status().isGone());
    }

    @Test
    void aRoleInviteStillWaitsForTheTeachersApproval() throws Exception {
        String roleInvite = inviteTokenService.generate(Role.PARENT);
        mockMvc.perform(get("/api/public/invites/preview").param("token", roleInvite))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PARENT"))
                .andExpect(jsonPath("$.className").doesNotExist())
                .andExpect(jsonPath("$.direct").value(false));

        register(email("maria"), roleInvite).andExpect(status().isCreated());
        confirmEmail(email("maria")).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        assertThat(account(email("maria")).getRole()).isNull();
    }

    @Test
    void aTamperedRoleInviteIsRefusedOnThePreview() throws Exception {
        mockMvc.perform(get("/api/public/invites/preview").param("token", "a.b.c")).andExpect(status().isGone());
    }
}
