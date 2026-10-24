package ro.mathlms.invite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ro.mathlms.auth.EmailService;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.outbox.OutboxHandler;
import ro.mathlms.outbox.OutboxPayloadCodec;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

/**
 * Tells one admin that an account registered through an admin invite link and is waiting for approval. Skipped when
 * there is nothing to tell any more: the newcomer was already decided on (or erased), or the admin is no longer an
 * active admin. Nothing is written; a mail failure throws, so the dispatcher backs off and retries.
 */
@Component
public class AdminInviteUsedEmailHandler implements OutboxHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminInviteUsedEmailHandler.class);

    private final OutboxPayloadCodec codec;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public AdminInviteUsedEmailHandler(OutboxPayloadCodec codec, UserRepository userRepository,
                                       EmailService emailService) {
        this.codec = codec;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Override
    public String eventType() {
        return OutboxEventTypes.ADMIN_INVITE_USED;
    }

    @Override
    public void handle(String payload) {
        AdminInviteUsedPayload event = codec.deserialize(payload, AdminInviteUsedPayload.class);
        User newcomer = userRepository.findById(event.newUserId()).orElse(null);
        if (newcomer == null || newcomer.isErased() || newcomer.getStatus() != AccountStatus.PENDING_APPROVAL) {
            log.info("Admin-invite alert for user {} skipped: no longer waiting for approval", event.newUserId());
            return;
        }
        User admin = userRepository.findById(event.adminId()).orElse(null);
        if (admin == null || admin.isErased() || admin.getRole() != Role.ADMIN
                || admin.getStatus() != AccountStatus.ACTIVE) {
            log.info("Admin-invite alert to user {} skipped: not an active admin", event.adminId());
            return;
        }
        emailService.sendAdminAlertNewPendingAdmin(admin.getEmail(), newcomer.getFullName(), newcomer.getEmail());
    }
}
