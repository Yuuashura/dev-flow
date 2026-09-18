package com.saas.notification_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Melempar MailException bila SMTP gagal, supaya listener me-NACK pesan
     * dan RabbitMQ bisa retry / mengirimnya ke DLQ. Jangan ditelan try-catch.
     */
    public void sendVerificationEmail(String toEmail, String fullName, String verificationUrl) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Verifikasi Email Anda - DevFlow");
            helper.setText(
                    "<p>Halo <strong>" + html(fullName) + "</strong>,</p>" +
                    "<p>Terima kasih telah mendaftar di DevFlow!</p>" +
                    "<p>Silakan klik link berikut untuk memverifikasi alamat email Anda:</p>" +
                    "<p><a href=\"" + htmlAttr(verificationUrl) + "\">" + html(verificationUrl) + "</a></p>" +
                    "<p>Link ini berlaku selama 24 jam.</p>" +
                    "<p>Jika Anda tidak melakukan pendaftaran ini, abaikan email ini.</p>" +
                    "<p>Salam,<br>Tim DevFlow</p>",
                    true);
            mailSender.send(msg);
        } catch (MessagingException e) {
            throw new MailSendException("Failed to construct verification email", e);
        }
        log.info("Email verifikasi berhasil dikirim ke: {}", toEmail);
    }

    /** Sama seperti di atas: kegagalan SMTP harus naik ke listener, bukan ditelan. */
    public void sendInvitationEmail(String toEmail, String workspaceName, String role, String acceptUrl, String inviterName) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Undangan Kolaborasi Workspace - DevFlow (" + workspaceName + ")");
            helper.setText(
                    "<p>Halo,</p>" +
                    "<p><strong>" + html(inviterName != null ? inviterName : "Seseorang") + "</strong>" +
                    " telah mengundang Anda untuk bergabung ke workspace" +
                    " <strong>\"" + html(workspaceName) + "\"</strong>" +
                    " sebagai <strong>" + html(role) + "</strong> di DevFlow SaaS!</p>" +
                    "<p>Klik link berikut untuk menerima undangan dan bergabung:</p>" +
                    "<p><a href=\"" + htmlAttr(acceptUrl) + "\">" + html(acceptUrl) + "</a></p>" +
                    "<p>Link ini berlaku selama 7 hari.<br>" +
                    "Jika Anda belum memiliki akun DevFlow, Anda akan dipandu untuk mendaftar terlebih dahulu sebelum menerima undangan.</p>" +
                    "<p>Salam,<br>Tim DevFlow</p>",
                    true);
            mailSender.send(msg);
        } catch (MessagingException e) {
            throw new MailSendException("Failed to construct invitation email", e);
        }
        log.info("Email undangan berhasil dikirim ke: {} untuk workspace: {} oleh: {}", toEmail, workspaceName, inviterName != null ? inviterName : "unknown");
    }

    private static String html(String s) {
        return (s == null ? "" : s)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String htmlAttr(String s) {
        return (s == null ? "" : s)
                .replace("&", "&amp;")
                .replace("\"", "&quot;");
    }
}
