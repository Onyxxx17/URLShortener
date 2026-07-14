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

/**
 * Sends transactional emails via the configured SMTP server (Gmail by default).
 * Configure {@code spring.mail.*} and {@code app.base-url} in
 * {@code application.properties} before use.
 */
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

    /**
     *
     * @param toEmail recipient's email address
     * @param token   the raw UUID token value (not URL-encoded — UUIDs are safe)
     */
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
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>Verify your email</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f4f5;font-family:Inter,Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="520" cellpadding="0" cellspacing="0"
                               style="background:#ffffff;border-radius:12px;overflow:hidden;
                                      box-shadow:0 4px 24px rgba(0,0,0,0.08);">

                          <!-- Header -->
                          <tr>
                            <td style="background:linear-gradient(135deg,#6366f1,#8b5cf6);
                                       padding:36px 40px;text-align:center;">
                              <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;
                                         letter-spacing:-0.3px;">🔗 URL Shortener</h1>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:40px;">
                              <h2 style="margin:0 0 12px;color:#111827;font-size:20px;font-weight:600;">
                                Verify your email address
                              </h2>
                              <p style="margin:0 0 24px;color:#6b7280;font-size:15px;line-height:1.6;">
                                Thanks for signing up! Click the button below to confirm
                                <strong>%s</strong> and activate your account.
                                This link expires in <strong>24 hours</strong>.
                              </p>

                              <!-- CTA Button -->
                              <table cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="border-radius:8px;
                                             background:linear-gradient(135deg,#6366f1,#8b5cf6);">
                                    <a href="%s"
                                       style="display:inline-block;padding:14px 32px;
                                              color:#ffffff;text-decoration:none;
                                              font-size:15px;font-weight:600;
                                              letter-spacing:0.2px;">
                                      Verify Email
                                    </a>
                                  </td>
                                </tr>
                              </table>

                              <p style="margin:24px 0 0;color:#9ca3af;font-size:13px;line-height:1.5;">
                                Or copy this link into your browser:<br/>
                                <a href="%s" style="color:#6366f1;word-break:break-all;">%s</a>
                              </p>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background:#f9fafb;padding:20px 40px;
                                       border-top:1px solid #e5e7eb;text-align:center;">
                              <p style="margin:0;color:#9ca3af;font-size:12px;">
                                If you didn't create an account, you can safely ignore this email.
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
}
