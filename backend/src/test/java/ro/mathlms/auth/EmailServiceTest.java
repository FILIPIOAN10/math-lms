package ro.mathlms.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailServiceTest {

    private static final String FRONTEND = "http://localhost:5173";
    private static final String FROM = "no-reply@mathlms.ro";

    private JavaMailSender mailSender;
    private EmailService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        service = new EmailService(mailSender, FRONTEND, FROM);
    }

    private SimpleMailMessage captureSentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void verificationEmailCarriesTokenLink() {
        service.sendVerificationEmail("ana@scoala.ro", "TOK123");

        SimpleMailMessage sent = captureSentMessage();
        assertThat(sent.getFrom()).isEqualTo(FROM);
        assertThat(sent.getTo()).containsExactly("ana@scoala.ro");
        assertThat(sent.getText()).contains(FRONTEND + "/verify-email?token=TOK123");
    }

    @Test
    void passwordResetEmailCarriesTokenLink() {
        service.sendPasswordResetEmail("ana@scoala.ro", "RESET9");

        SimpleMailMessage sent = captureSentMessage();
        assertThat(sent.getTo()).containsExactly("ana@scoala.ro");
        assertThat(sent.getText()).contains(FRONTEND + "/reset-password?token=RESET9");
    }

    @Test
    void approvedEmailPointsToLogin() {
        service.sendAccountApprovedEmail("ana@scoala.ro");

        SimpleMailMessage sent = captureSentMessage();
        assertThat(sent.getTo()).containsExactly("ana@scoala.ro");
        assertThat(sent.getText()).contains(FRONTEND + "/login");
    }

    @Test
    void assignmentReminderNamesTheQuizTheDeadlineInSchoolTimeAndLinksToIt() {
        // 2026-10-06 10:00 UTC is 13:00 in Bucharest (summer time until the last Sunday of October)
        service.sendAssignmentReminder("ana@scoala.ro", "Ana Pop", "Simulare EN",
                java.time.Instant.parse("2026-10-06T10:00:00Z"), 10L);

        SimpleMailMessage sent = captureSentMessage();
        assertThat(sent.getTo()).containsExactly("ana@scoala.ro");
        assertThat(sent.getSubject()).contains("Simulare EN");
        assertThat(sent.getText()).contains("Ana Pop").contains("Simulare EN")
                .contains("6 octombrie 2026").contains("13:00")
                .contains(FRONTEND + "/quizzes/10/take");
    }
}
