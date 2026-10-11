package ro.mathlms.invite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.invite.InviteLinkDtos.InviteLinkDto;
import ro.mathlms.invite.InviteLinkDtos.InvitePreviewDto;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * T1 one-step onboarding. The teacher mints a link on a class ("join Clasa a 8-a") or on a student ("be Ana's parent");
 * whoever registers through it becomes active as soon as their email is confirmed — enrolled in the class, or linked to
 * the student — with no approval, enrolment or linking step left for the teacher. The teacher already vouched for them
 * by sharing the link; the cap, the 7-day expiry and revocation bound what a forwarded link can do.
 */
@Service
public class InviteLinkService {

    /** The shape of a stored link code: URL-safe base64, no dots — a role invite (a JWT) always has two. */
    private static final Pattern STORED_CODE = Pattern.compile("[A-Za-z0-9_-]{20,64}");
    private static final String NOT_A_LINK = "Linkul de invitație nu e valid. Cere profesorului un link nou.";

    private final InviteLinkRepository linkRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final Clock clock;
    private final String frontendBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public InviteLinkService(InviteLinkRepository linkRepository, SchoolClassRepository schoolClassRepository,
                             UserRepository userRepository, EnrollmentRepository enrollmentRepository, Clock clock,
                             @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.linkRepository = linkRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** True for a stored link code, false for anything else (notably a signed role invite). */
    public static boolean isStoredCode(String token) {
        return token != null && STORED_CODE.matcher(token).matches();
    }

    // --- teacher side ---

    /** A new link that enrols up to {@code maxUses} students (null = the default) in the class. */
    @Transactional
    public InviteLinkDto createClassInvite(Long classId, Integer maxUses) {
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new InviteNotFoundException("Clasa nu există."));
        int cap = maxUses == null ? InviteLink.DEFAULT_CLASS_USES : maxUses;
        return toDto(linkRepository.save(InviteLink.forClass(newCode(), schoolClass, cap, clock.instant())));
    }

    /** The class's links that still work or have only filled up — what the teacher can copy or revoke. */
    @Transactional(readOnly = true)
    public List<InviteLinkDto> activeClassInvites(Long classId) {
        if (!schoolClassRepository.existsById(classId)) {
            throw new InviteNotFoundException("Clasa nu există.");
        }
        return linkRepository.findActiveForClass(classId, clock.instant()).stream().map(this::toDto).toList();
    }

    /**
     * A single-use link for the parent of an active student who has none yet. Any earlier unused one for this student
     * is revoked, so only the latest link handed out works.
     */
    @Transactional
    public InviteLinkDto createParentInvite(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new InviteNotFoundException("Elevul nu există."));
        if (student.getRole() != Role.STUDENT || student.getStatus() != AccountStatus.ACTIVE || student.isErased()) {
            throw new InviteRejectedException("Doar pentru un elev activ se poate genera un link pentru părinte.");
        }
        if (student.getParent() != null) {
            throw new InviteRejectedException(student.getFullName() + " are deja un părinte legat ("
                    + student.getParent().getFullName() + "). Schimbă legătura din lista „Leagă părinți de elevi”.");
        }
        Instant now = clock.instant();
        linkRepository.findOpenForStudent(studentId, now).forEach(old -> old.revoke(now));
        return toDto(linkRepository.save(InviteLink.forParentOf(newCode(), student, now)));
    }

    /** Stops a link. Accounts that registered through it before still finish joining. */
    @Transactional
    public void revoke(Long linkId) {
        InviteLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new InviteNotFoundException("Linkul de invitație nu există."));
        link.revoke(clock.instant());
    }

    // --- newcomer side ---

    /** What the register page shows for a usable link; 410 with the reason otherwise. Takes nothing. */
    @Transactional(readOnly = true)
    public InvitePreviewDto preview(String code) {
        InviteLink link = usable(code);
        return new InvitePreviewDto(link.getKind().role(),
                link.getKind() == InviteLinkKind.CLASS ? link.getSchoolClass().getName() : null, true);
    }

    /**
     * Takes one use of the link for a registration happening in the caller's transaction (so a registration that
     * fails afterwards — e.g. the email is taken — gives the use back). 410 with the reason if it cannot be used.
     */
    @Transactional
    public InviteLink claim(String code) {
        InviteLink link = usable(code);
        if (linkRepository.claimUse(link.getId(), clock.instant()) == 0) {
            // Someone took the last use (or the teacher revoked it) between the read and the claim.
            InviteLink now = linkRepository.findById(link.getId()).orElseThrow(() -> new InviteUnavailableException(NOT_A_LINK));
            throw new InviteUnavailableException(now.unavailableReason(clock.instant()).orElse(NOT_A_LINK));
        }
        return linkRepository.findById(link.getId()).orElseThrow(() -> new InviteUnavailableException(NOT_A_LINK));
    }

    /**
     * Finishes joining for an account that registered through a stored link and is now waiting for approval (email
     * just confirmed, or a Google sign-up): activates it with the link's role and enrols / links it. Returns whether it
     * did — false for every other account (no link, already decided, or its link was deleted with its class), which
     * then simply waits in the approval queue as before — as does a parent whose student erased their account
     * meanwhile. A parent is only linked if the student still has no parent (someone else may have been linked in the
     * meantime); the parent account is activated either way.
     */
    @Transactional
    public boolean completeOnboarding(User user) {
        if (user.getInviteLinkId() == null || user.getStatus() != AccountStatus.PENDING_APPROVAL) {
            return false;
        }
        InviteLink link = linkRepository.findById(user.getInviteLinkId()).orElse(null);
        if (link == null || (link.getKind() == InviteLinkKind.PARENT && link.getStudent().isErased())) {
            return false; // nothing left to join (class deleted, or the child erased their account): the teacher decides
        }
        user.approve(link.getKind().role());
        userRepository.save(user);
        switch (link.getKind()) {
            case CLASS -> {
                SchoolClass schoolClass = link.getSchoolClass();
                if (!enrollmentRepository.existsByStudentIdAndSchoolClassId(user.getId(), schoolClass.getId())) {
                    enrollmentRepository.save(new Enrollment(user, schoolClass));
                }
            }
            case PARENT -> {
                User student = link.getStudent();
                if (student.getRole() == Role.STUDENT && student.getParent() == null) {
                    student.linkParent(user);
                    userRepository.save(student);
                }
            }
        }
        return true;
    }

    // --- helpers ---

    private InviteLink usable(String code) {
        if (!isStoredCode(code)) {
            throw new InviteUnavailableException(NOT_A_LINK);
        }
        InviteLink link = linkRepository.findByCode(code)
                .orElseThrow(() -> new InviteUnavailableException(NOT_A_LINK));
        link.unavailableReason(clock.instant()).ifPresent(reason -> {
            throw new InviteUnavailableException(reason);
        });
        return link;
    }

    /** 24 random bytes as URL-safe base64: 32 characters, unguessable. */
    private String newCode() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private InviteLinkDto toDto(InviteLink link) {
        return new InviteLinkDto(link.getId(), link.getKind(), frontendBaseUrl + "/register?token=" + link.getCode(),
                link.getUses(), link.getMaxUses(), link.getExpiresAt(), link.getCreatedAt(),
                link.getSchoolClass() == null ? null : link.getSchoolClass().getName(),
                link.getStudent() == null ? null : link.getStudent().getFullName());
    }
}
