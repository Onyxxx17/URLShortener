package com.ayth.urlshortener.email;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Plain unit test — no Spring context. JavaMailSender is mocked but
 * createMimeMessage() returns a real MimeMessage backed by an empty Session,
 * so the built message's headers/body can actually be inspected rather than
 * just asserting that some mock method was called.
 */
class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5173");
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendVerificationEmail_sendsHtmlMessageWithBaseUrlVerificationLink() throws Exception {
        emailService.sendVerificationEmail("user@example.com", "tok-123");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getFrom()[0].toString()).isEqualTo("noreply@example.com");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat(sent.getSubject()).isEqualTo("Verify your email - URL Shortener");

        String body = contentAsString(sent);
        assertThat(body).contains("http://localhost:8080/verify-email?token=tok-123");
        assertThat(body).contains("user@example.com");
    }

    @Test
    void sendPasswordResetEmail_sendsHtmlMessageWithFrontendUrlResetLink() throws Exception {
        emailService.sendPasswordResetEmail("reset@example.com", "tok-456");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getSubject()).isEqualTo("Reset your password - URL Shortener");
        String body = contentAsString(sent);
        assertThat(body).contains("http://localhost:5173/reset-password?token=tok-456");
    }

    @Test
    void sendVerificationEmail_wrapsMailSenderFailureInRuntimeException() {
        doThrow(new org.springframework.mail.MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendVerificationEmail("user@example.com", "tok"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send verification email");
    }

    private String contentAsString(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = contentAsString(multipart.getBodyPart(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
