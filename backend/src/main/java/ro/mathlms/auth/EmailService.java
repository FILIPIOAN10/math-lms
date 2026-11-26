package ro.mathlms.auth;

import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the transactional emails of the onboarding flow. Verification and reset
 * links point at the frontend, which forwards the embedded token back to the API.
 *
 * <p>The sender is the app, not whoever owns the SMTP account: "Math LMS &lt;MAIL_FROM&gt;", so an email relay
 * (Brevo, Amazon SES, Mailgun…) can send from a no-reply address while the login stays the relay's. With no
 * MAIL_FROM the SMTP login is used (Gmail rewrites any other address to it anyway), still under the app's name.
 * MAIL_REPLY_TO, when set, is where a parent's "Reply" lands; otherwise there is none.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String frontendBaseUrl;
    private final String from;
    private final String replyTo;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.frontend.base-url}") String frontendBaseUrl,
                        @Value("${app.mail.from:}") String fromAddress,
                        @Value("${app.mail.from-name:Math LMS}") String fromName,
                        @Value("${app.mail.reply-to:}") String replyTo,
                        @Value("${spring.mail.username:}") String smtpUser) {
        this.mailSender = mailSender;
        this.frontendBaseUrl = frontendBaseUrl;
        this.from = sender(firstSet(fromAddress, smtpUser, "no-reply@mathlms.ro"), fromName);
        this.replyTo = isBlank(replyTo) ? null : replyTo.trim();
    }

    /** "Math LMS <no-reply@x.ro>", the name encoded so diacritics survive; only the address when there is no name. */
    static String sender(String address, String name) {
        if (isBlank(name)) return address;
        try {
            return new InternetAddress(address, name.trim(), "UTF-8").toString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is always supported", e);
        }
    }

    private static String firstSet(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        throw new IllegalArgumentException("no value");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public void sendVerificationEmail(String email, String token) {
        String link = frontendBaseUrl + "/verify-email?token=" + token;
        send(email, "Confirmă-ți contul MathLMS",
                "Salut!\n\n"
                        + "Confirmă-ți adresa de email accesând linkul de mai jos:\n"
                        + link + "\n\n"
                        + "Linkul expiră în 24 de ore. Dacă nu tu ai creat contul, ignoră acest mesaj.");
    }

    public void sendPasswordResetEmail(String email, String token) {
        String link = frontendBaseUrl + "/reset-password?token=" + token;
        send(email, "Resetare parolă MathLMS",
                "Ai cerut resetarea parolei.\n\n"
                        + "Setează o parolă nouă accesând:\n"
                        + link + "\n\n"
                        + "Linkul expiră într-o oră. Dacă nu tu ai cerut resetarea, ignoră acest mesaj.");
    }

    /** Best-effort notice; security state changes must not appear to fail if the mail relay is unavailable. */
    public void sendPasswordChangedNotice(String email) {
        sendSecurityNotice(email, "password-changed",
                "Parola contului tău Math LMS a fost schimbată. Dacă nu ai făcut tu această schimbare, contactează imediat administratorul școlii.");
    }

    public void sendTwoFactorDisabledNotice(String email) {
        sendSecurityNotice(email, "two-factor-disabled",
                "Autentificarea în doi pași a fost dezactivată pentru contul tău Math LMS. Dacă nu ai făcut tu această schimbare, contactează imediat administratorul școlii.");
    }

    public void sendRecoveryCodesRegeneratedNotice(String email) {
        sendSecurityNotice(email, "recovery-codes-regenerated",
                "Codurile de recuperare pentru contul tău Math LMS au fost regenerate. Codurile vechi nu mai funcționează. Dacă nu ai făcut tu această schimbare, contactează imediat administratorul școlii.");
    }

    private void sendSecurityNotice(String email, String event, String body) {
        try {
            send(email, "Alertă de securitate Math LMS", body + "\n\nAutentifică-te aici: " + frontendBaseUrl + "/login");
        } catch (MailException e) {
            // Do not log the recipient, token, message body, or transport exception (which may contain PII).
            log.warn("Could not deliver security notification: {}", event);
        }
    }

    public void sendErasureConfirmationEmail(String email, String token) {
        String link = frontendBaseUrl + "/erase-account?token=" + token;
        send(email, "Confirmă ștergerea contului MathLMS",
                "Ai cerut ștergerea definitivă a contului tău.\n\n"
                        + "Confirmă accesând linkul de mai jos. Datele tale personale vor fi șterse,\n"
                        + "iar notele vor fi păstrate anonimizat:\n"
                        + link + "\n\n"
                        + "Linkul expiră în 30 de minute. Dacă nu tu ai cerut ștergerea, ignoră acest mesaj\n"
                        + "și schimbă-ți parola — cineva ar putea avea acces la contul tău.");
    }

    public void sendAccountApprovedEmail(String email) {
        send(email, "Contul tău MathLMS a fost aprobat",
                "Contul tău a fost aprobat de profesor.\n\n"
                        + "Te poți autentifica aici:\n"
                        + frontendBaseUrl + "/login");
    }

    public void sendResultReadyToStudent(String email, String studentName, String quizTitle,
                                         int score, int maxScore, Long attemptId) {
        send(email, "Rezultatul testului „" + quizTitle + "” este gata",
                "Salut, " + studentName + "!\n\n"
                        + "Profesorul ți-a corectat testul „" + quizTitle + "”.\n"
                        + "Nota ta: " + score + " / " + maxScore + " puncte" + percent(score, maxScore) + ".\n\n"
                        + "Vezi rezolvarea și baremul aici:\n"
                        + frontendBaseUrl + "/quizzes/attempts/" + attemptId + "/result");
    }

    public void sendResultReadyToParent(String email, String studentName, String quizTitle,
                                        int score, int maxScore, Long studentId, Long attemptId) {
        send(email, "Rezultatul lui " + studentName + " la „" + quizTitle + "” este gata",
                "Bună!\n\n"
                        + "Testul „" + quizTitle + "” al lui " + studentName + " a fost corectat.\n"
                        + "Nota: " + score + " / " + maxScore + " puncte" + percent(score, maxScore) + ".\n\n"
                        + "Vezi detaliile aici:\n"
                        + frontendBaseUrl + "/parent/children/" + studentId + "/attempts/" + attemptId);
    }

    /** The school's time zone: deadlines are shown in the teacher's and students' local time, not in UTC. */
    private static final java.time.ZoneId SCHOOL_ZONE = java.time.ZoneId.of("Europe/Bucharest");
    private static final java.time.format.DateTimeFormatter DUE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy 'la' HH:mm", java.util.Locale.forLanguageTag("ro"));

    public void sendAssignmentReminder(String email, String studentName, String quizTitle,
                                       java.time.Instant dueAt, Long quizId) {
        send(email, "Temă de făcut: „" + quizTitle + "”",
                "Salut, " + studentName + "!\n\n"
                        + "Tema „" + quizTitle + "” are termen " + DUE_FORMAT.format(dueAt.atZone(SCHOOL_ZONE)) + ".\n"
                        + "Nu ai predat-o încă. O poți face aici:\n"
                        + frontendBaseUrl + "/quizzes/" + quizId + "/take");
    }

    /** The parent's copy: the child has not handed in yet; the link opens the child's page (homework + results). */
    public void sendAssignmentReminderToParent(String email, String studentName, String quizTitle,
                                               java.time.Instant dueAt, Long studentId) {
        send(email, studentName + " are de predat tema „" + quizTitle + "”",
                "Bună!\n\n"
                        + studentName + " are tema „" + quizTitle + "” cu termen "
                        + DUE_FORMAT.format(dueAt.atZone(SCHOOL_ZONE)) + ".\n"
                        + "Nu a predat-o încă. Temele și rezultatele copilului sunt aici:\n"
                        + frontendBaseUrl + "/parent/children/" + studentId);
    }

    /** The GDPR archive is ready: a single-use link, valid as long as the archive is kept. */
    public void sendGdprExportReadyEmail(String email, String token, long validDays) {
        String link = frontendBaseUrl + "/api/public/gdpr/export/download?token=" + token;
        send(email, "Datele tale din MathLMS sunt gata",
                "Bună!\n\n"
                        + "Arhiva cu datele tale (profil, clase, teste, note, comentarii, poze) e gata. Descarc-o de aici:\n"
                        + link + "\n\n"
                        + "Linkul merge o singură dată și expiră în " + validDays + " zile. Dacă ai nevoie de el din nou,\n"
                        + "cere-l din „Contul meu” → „Datele tale”. Dacă nu tu ai cerut arhiva, schimbă-ți parola.");
    }

    /** To every active admin: someone used an admin invite link and waits for approval (never activated by the link). */
    public void sendAdminAlertNewPendingAdmin(String adminEmail, String newUserName, String newUserEmail) {
        send(adminEmail, "Cont nou de profesor/admin de aprobat în MathLMS",
                "Bună!\n\n"
                        + newUserName + " (" + newUserEmail + ") s-a înregistrat printr-un link de invitație pentru "
                        + "profesor/admin.\n"
                        + "Contul NU a fost activat automat: așteaptă aprobarea ta.\n\n"
                        + "Aprobă-l sau respinge-l aici (dacă nu știi cine e, respinge-l și revocă linkul):\n"
                        + frontendBaseUrl + "/admin/students");
    }

    private static String percent(int score, int maxScore) {
        return maxScore == 0 ? "" : " (" + Math.round(score * 100f / maxScore) + "%)";
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        if (replyTo != null) message.setReplyTo(replyTo);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
