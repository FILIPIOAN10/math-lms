package ro.mathlms.invite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import ro.mathlms.outbox.OutboxEventPublisher;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.outbox.OutboxPayloadCodec;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T1 end to end on real Postgres, through the real auth endpoints: a newcomer who joins through a class or parent link
 * is active, enrolled / linked as soon as the email is confirmed — no approval, enrolment or linking by the teacher —
 * while the cap, expiry and revocation keep a forwarded link from letting strangers in. An admin link is the exception:
 * single-use, 24 hours, and its account always waits for an existing admin's approval (every active admin is emailed).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InviteLinkIntegrationTest {

    @MockitoBean private EmailService emailService;
    /** Captures the alert events; the tests then hand them to the real handler (no dispatcher timing involved). */
    @MockitoBean private OutboxEventPublisher outboxEventPublisher;

    @Autowired private MockMvc mockMvc;
    @Autowired private InviteLinkService inviteLinkService;
    @Autowired private InviteLinkRepository linkRepository;
    @Autowired private InviteTokenService inviteTokenService;
    @Autowired private VerificationTokenService verificationTokenService;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AdminInviteUsedEmailHandler adminInviteUsedEmailHandler;
    @Autowired private OutboxPayloadCodec codec;

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

    private User activeAdmin(String who) {
        User admin = User.registerLocal(email(who), "Profesor Existent", "HASH", Role.ADMIN);
        admin.verifyEmail();
        admin.approve(Role.ADMIN);
        return userRepository.save(admin);
    }

    /** The alert payloads published so far for {@code newcomer}, one per admin. */
    private java.util.List<AdminInviteUsedPayload> alertsFor(User newcomer) {
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher, atLeastOnce()).publish(eq(OutboxEventTypes.ADMIN_INVITE_USED), payloads.capture());
        return payloads.getAllValues().stream().map(AdminInviteUsedPayload.class::cast)
                .filter(p -> p.newUserId().equals(newcomer.getId())).toList();
    }

    // --- admin (teacher) link ---

    @Test
    void aTeacherWhoJoinsThroughTheAdminLinkWaitsForApprovalAndEveryActiveAdminIsEmailed() throws Exception {
        User existing = activeAdmin("director");
        InviteLinkDto link = inviteLinkService.createAdminInvite();
        assertThat(link.maxUses()).isEqualTo(1);
        assertThat(Duration.between(link.createdAt(), link.expiresAt())).isEqualTo(InviteLink.ADMIN_TTL);

        register(email("profa"), code(link)).andExpect(status().isCreated());
        confirmEmail(email("profa")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        User teacher = account(email("profa"));
        assertThat(teacher.getStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(teacher.getRole()).isNull(); // the role comes only with an admin's approval
        // one event per active admin, ids only; the real handler turns ours into an email
        AdminInviteUsedPayload ours = alertsFor(teacher).stream()
                .filter(p -> p.adminId().equals(existing.getId())).findFirst().orElseThrow();
        adminInviteUsedEmailHandler.handle(codec.serialize(ours));
        verify(emailService).sendAdminAlertNewPendingAdmin(existing.getEmail(), teacher.getFullName(), teacher.getEmail());
        // one use: the next one to open the same link is turned away
        register(email("al-doilea"), code(link)).andExpect(status().isGone());
        assertThat(userRepository.findByEmail(email("al-doilea"))).isEmpty();
    }

    @Test
    void theAdminAlertIsSkippedOnceTheNewcomerWasDecidedOn() throws Exception {
        User existing = activeAdmin("director");
        InviteLinkDto link = inviteLinkService.createAdminInvite();
        register(email("profa"), code(link)).andExpect(status().isCreated());
        confirmEmail(email("profa")).andExpect(status().isOk());
        User teacher = account(email("profa"));
        teacher.approve(Role.ADMIN); // approved before the outbox got to the email
        userRepository.save(teacher);

        AdminInviteUsedPayload ours = alertsFor(teacher).stream()
                .filter(p -> p.adminId().equals(existing.getId())).findFirst().orElseThrow();
        adminInviteUsedEmailHandler.handle(codec.serialize(ours));

        verify(emailService, never()).sendAdminAlertNewPendingAdmin(anyString(), any(), any());
    }

    @Test
    void anExpiredAdminLinkTurnsNewcomersAway() throws Exception {
        InviteLinkDto link = inviteLinkService.createAdminInvite();
        InviteLink stored = linkRepository.findById(link.id()).orElseThrow();
        ReflectionTestUtils.setField(stored, "expiresAt", Instant.now().minusSeconds(60));
        linkRepository.save(stored);

        register(email("intarziat"), code(link))
                .andExpect(status().isGone())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("expirat")));
        assertThat(userRepository.findByEmail(email("intarziat"))).isEmpty();
    }

    @Test
    void aRevokedAdminLinkLeavesTheListAndTurnsNewcomersAway() throws Exception {
        InviteLinkDto link = inviteLinkService.createAdminInvite();
        assertThat(inviteLinkService.activeAdminInvites()).extracting(InviteLinkDto::id).contains(link.id());

        inviteLinkService.revoke(link.id());

        assertThat(inviteLinkService.activeAdminInvites()).extracting(InviteLinkDto::id).doesNotContain(link.id());
        register(email("strain"), code(link))
                .andExpect(status().isGone())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("oprit")));
        assertThat(userRepository.findByEmail(email("strain"))).isEmpty();
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

        mockMvc.perform(get("/api/public/invites/preview").param("token", "0".repeat(24))) // well-formed, but no such link
                .andExpect(status().isGone());
    }

    @Test
    void aPreviewForAnAdminLinkShowsItIsNotDirect() throws Exception {
        InviteLinkDto link = inviteLinkService.createAdminInvite();

        mockMvc.perform(get("/api/public/invites/preview").param("token", code(link)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER")) // the link asks to be a teacher, never an admin
                .andExpect(jsonPath("$.direct").value(false)); // the page must not promise instant access
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
