package ro.mathlms.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the transactional emails of the onboarding flow. Verification and reset
 * links point at the frontend, which forwards the embedded token back to the API.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String frontendBaseUrl;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.frontend.base-url}") String frontendBaseUrl,
                        @Value("${spring.mail.username:no-reply@mathlms.ro}") String fromAddress) {
        this.mailSender = mailSender;
        this.frontendBaseUrl = frontendBaseUrl;
        this.fromAddress = fromAddress;
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

    private static String percent(int score, int maxScore) {
        return maxScore == 0 ? "" : " (" + Math.round(score * 100f / maxScore) + "%)";
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
