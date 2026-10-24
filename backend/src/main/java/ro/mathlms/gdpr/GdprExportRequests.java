package ro.mathlms.gdpr;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.auth.EmailService;
import ro.mathlms.auth.TokenPurpose;
import ro.mathlms.auth.UserNotFoundException;
import ro.mathlms.auth.VerificationTokenService;
import ro.mathlms.outbox.OutboxEventPublisher;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * GDPR Art. 15, deferred: asking queues the build (outbox) and returns at once; the worker assembles the ZIP, stores
 * it for {@link GdprExport#TTL} and emails a single-use link; opening the link streams it. The link is emailed, never
 * returned, so a hijacked session alone cannot walk off with someone's whole account. One live export per account:
 * asking again while it is being built is answered, not queued; asking again once it is ready emails a fresh link.
 */
@Service
public class GdprExportRequests {

    private static final Logger log = LoggerFactory.getLogger(GdprExportRequests.class);

    /** What asking did, for the page to say. */
    public enum Outcome { QUEUED, ALREADY_BUILDING, LINK_RESENT }

    /** A downloaded archive and its file name. */
    public record Download(byte[] zip, String fileName) {
    }

    private final GdprExportRepository repository;
    private final UserRepository userRepository;
    private final GdprExportService exportService;
    private final OutboxEventPublisher publisher;
    private final VerificationTokenService tokenService;
    private final EmailService emailService;
    private final Clock clock;

    public GdprExportRequests(GdprExportRepository repository, UserRepository userRepository,
                              GdprExportService exportService, OutboxEventPublisher publisher,
                              VerificationTokenService tokenService, EmailService emailService, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.exportService = exportService;
        this.publisher = publisher;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.clock = clock;
    }

    @Transactional
    public Outcome request(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("No account"));
        Optional<GdprExport> live = repository.findLive(user.getId());
        if (live.isPresent() && live.get().getStatus() == GdprExport.Status.PENDING) {
            return Outcome.ALREADY_BUILDING;
        }
        if (live.isPresent() && !live.get().isExpired(clock.instant())) {
            sendLink(user); // also how someone recovers a lost (or already used) link
            return Outcome.LINK_RESENT;
        }
        live.ifPresent(GdprExport::expire); // past its date but not purged yet
        repository.flush(); // the one-live index must see it expired before the new row goes in
        GdprExport export = repository.save(new GdprExport(user.getId(), clock.instant()));
        publisher.publish(OutboxEventTypes.GDPR_EXPORT_BUILD, new GdprExportPayload(export.getId()));
        return Outcome.QUEUED;
    }

    /**
     * Builds one export and emails the link; runs inside the outbox processor's transaction, so a mail failure rolls
     * the archive back and the build is retried. Nothing to do for an export that is not PENDING any more.
     */
    @Transactional
    public void build(Long exportId) {
        GdprExport export = repository.findById(exportId).orElse(null);
        if (export == null || export.getStatus() != GdprExport.Status.PENDING) {
            return;
        }
        User user = userRepository.findById(export.getUserId()).orElse(null);
        if (user == null || user.isErased()) {
            repository.delete(export);
            return;
        }
        export.ready(exportService.exportZip(user.getEmail()), clock.instant());
        repository.save(export);
        sendLink(user);
    }

    /** Spends the emailed token and hands back the archive (once per link; ask again for a new one). */
    @Transactional
    public Download download(String token) {
        String email;
        try {
            email = tokenService.verify(token, TokenPurpose.GDPR_EXPORT);
        } catch (JwtException e) {
            throw new GdprExportUnavailableException("Linkul a expirat sau a fost deja folosit. "
                    + "Cere un link nou din „Contul meu” → „Datele tale”.");
        }
        User user = userRepository.findByEmail(email).orElseThrow(() -> unavailable());
        GdprExport export = repository.findLive(user.getId())
                .filter(e -> e.getStatus() == GdprExport.Status.READY && !e.isExpired(clock.instant()))
                .orElseThrow(GdprExportRequests::unavailable);
        export.downloaded(clock.instant());
        return new Download(export.getArchive(), "datele-mele-mathlms.zip");
    }

    /** Drops the bytes of every archive past its date; the rows stay as the record that the request was answered. */
    @Transactional
    public int purgeExpired() {
        List<GdprExport> expired = repository.findReadyExpiredBefore(clock.instant());
        expired.forEach(GdprExport::expire);
        if (!expired.isEmpty()) {
            log.info("Dropped {} expired GDPR export archive(s)", expired.size());
        }
        return expired.size();
    }

    private void sendLink(User user) {
        emailService.sendGdprExportReadyEmail(user.getEmail(), tokenService.generate(user.getEmail(), TokenPurpose.GDPR_EXPORT),
                GdprExport.TTL.toDays());
    }

    private static GdprExportUnavailableException unavailable() {
        return new GdprExportUnavailableException("Arhiva nu mai e disponibilă. Cere una nouă din „Contul meu”.");
    }
}
