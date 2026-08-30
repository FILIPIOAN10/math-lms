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

    public void sendAccountApprovedEmail(String email) {
        send(email, "Contul tău MathLMS a fost aprobat",
                "Contul tău a fost aprobat de profesor.\n\n"
                        + "Te poți autentifica aici:\n"
                        + frontendBaseUrl + "/login");
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
