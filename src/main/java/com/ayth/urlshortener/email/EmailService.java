package com.ayth.urlshortener.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    // ── Public API ────────────────────────────────────────────────────────────

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        log.debug("[MAIL] Preparing to send verification email to {}", toEmail);
        String link = baseUrl + "/verify-email?token=" + token;
        String subject = "Verify your email - URL Shortener";
        
        log.debug("[MAIL] Building HTML for verification email to {}", toEmail);
        String html = buildVerificationHtml(toEmail, link);

        sendHtml(toEmail, subject, html);
        log.info("[MAIL] Verification email sent to {}", toEmail);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void sendHtml(String to, String subject, String html) {
        try {
            log.debug("[MAIL] Creating MimeMessage for {}", to);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);   // true = isHtml
            
            log.debug("[MAIL] Executing mailSender.send() for {}", to);
            mailSender.send(message);
            log.debug("[MAIL] Successfully executed mailSender.send() for {}", to);
        } catch (Exception ex) {
            log.error("[MAIL] Failed to send email to {}: {}", to, ex.getMessage(), ex);
            throw new RuntimeException("Failed to send verification email. Please try again later.", ex);
        }
    }

    private String buildVerificationHtml(String email, String verificationLink) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Verify your email</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #f4f7fa; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #333333;">
                    <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="table-layout: fixed; background-color: #f4f7fa; margin: 0 auto;">
                        <tr>
                            <td align="center" style="padding: 40px 10px;">
                                <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width: 500px; background-color: #ffffff; border-radius: 8px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);">
                                    <tr>
                                        <td align="center" style="padding: 40px 30px 30px;">
                                            <div style="width: 50px; height: 50px; background-color: #eef2ff; border-radius: 50%%; display: inline-block; margin-bottom: 20px;">
                                                <h2 style="margin: 0; color: #4f46e5; line-height: 50px; font-size: 24px;">🔗</h2>
                                            </div>
                                            <h1 style="margin: 0 0 15px; font-size: 24px; font-weight: 700; color: #111827; letter-spacing: -0.5px;">Verify your email address</h1>
                                            <p style="margin: 0 0 25px; font-size: 16px; line-height: 1.6; color: #4b5563;">
                                                Welcome to <strong>URL Shortener</strong>! Please confirm that you want to use <strong>%s</strong> as your account email address.
                                            </p>
                                            <a href="%s" style="display: inline-block; padding: 14px 32px; background-color: #4f46e5; color: #ffffff; text-decoration: none; font-size: 16px; font-weight: 600; border-radius: 6px; box-shadow: 0 2px 4px rgba(79, 70, 229, 0.3);">Verify Email</a>
                                            <p style="margin: 25px 0 0; font-size: 14px; line-height: 1.5; color: #6b7280;">
                                                Or copy and paste this link into your browser:<br>
                                                <a href="%s" style="color: #4f46e5; text-decoration: none; word-break: break-all;">%s</a>
                                            </p>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td align="center" style="padding: 25px 30px; background-color: #f9fafb; border-bottom-left-radius: 8px; border-bottom-right-radius: 8px; border-top: 1px solid #f3f4f6;">
                                            <p style="margin: 0; font-size: 12px; color: #9ca3af; line-height: 1.5;">
                                                If you did not create an account, you can safely ignore this email.
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(email, verificationLink, verificationLink, verificationLink);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        log.debug("[MAIL] Preparing to send password reset email to {}", toEmail);
        String link = baseUrl.replace(":8080", ":5173") + "/reset-password?token=" + token;
        String subject = "Reset your password - URL Shortener";

        String html = buildPasswordResetHtml(toEmail, link);
        sendHtml(toEmail, subject, html);
        log.info("[MAIL] Password reset email sent to {}", toEmail);
    }

    private String buildPasswordResetHtml(String email, String resetLink) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Reset your password</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #f4f7fa; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #333333;">
                    <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="table-layout: fixed; background-color: #f4f7fa; margin: 0 auto;">
                        <tr>
                            <td align="center" style="padding: 40px 10px;">
                                <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width: 500px; background-color: #ffffff; border-radius: 8px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);">
                                    <tr>
                                        <td align="center" style="padding: 40px 30px 30px;">
                                            <div style="width: 50px; height: 50px; background-color: #fef2f2; border-radius: 50%%; display: inline-block; margin-bottom: 20px;">
                                                <h2 style="margin: 0; color: #dc2626; line-height: 50px; font-size: 24px;">🔒</h2>
                                            </div>
                                            <h1 style="margin: 0 0 15px; font-size: 24px; font-weight: 700; color: #111827; letter-spacing: -0.5px;">Reset your password</h1>
                                            <p style="margin: 0 0 25px; font-size: 16px; line-height: 1.6; color: #4b5563;">
                                                We received a password reset request for <strong>%s</strong>. Click the button below to choose a new password. This link expires in 1 hour.
                                            </p>
                                            <a href="%s" style="display: inline-block; padding: 14px 32px; background-color: #111827; color: #ffffff; text-decoration: none; font-size: 16px; font-weight: 600; border-radius: 6px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.15);">Reset Password</a>
                                            <p style="margin: 25px 0 0; font-size: 14px; line-height: 1.5; color: #6b7280;">
                                                Or copy and paste this link into your browser:<br>
                                                <a href="%s" style="color: #111827; text-decoration: none; word-break: break-all;">%s</a>
                                            </p>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td align="center" style="padding: 25px 30px; background-color: #f9fafb; border-bottom-left-radius: 8px; border-bottom-right-radius: 8px; border-top: 1px solid #f3f4f6;">
                                            <p style="margin: 0; font-size: 12px; color: #9ca3af; line-height: 1.5;">
                                                If you did not request a password reset, you can safely ignore this email. Your password will not be changed.
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(email, resetLink, resetLink, resetLink);
    }
}
