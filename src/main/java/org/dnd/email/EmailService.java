package org.dnd.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  @Value("${app.frontend-path-verify-email}")
  private String frontendPathVerifyEmail;

  @Value("${app.frontend-path-reset-password}")
  private String frontendPathResetPassword;

  @Value("${app.mail.from}")
  private String from;

  @Value("classpath:email/verification-email.html")
  private Resource verificationEmailTemplate;

  @Value("classpath:email/change-existing-email.html")
  private Resource changeEmailTemplate;

  @Value("classpath:email/reset-password-email.html")
  private Resource resetPasswordTemplate;

  public void sendVerificationEmail(String username, String email, String verificationToken) {
    String verificationUrl = UriComponentsBuilder
            .fromUriString(frontendUrl)
            .path(frontendPathVerifyEmail)
            .queryParam("token", verificationToken)
            .toUriString();

    try {
      String html = loadVerificationTemplate(username, verificationUrl);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

      helper.setFrom(from);
      helper.setTo(email);
      helper.setSubject("Verify Your email");
      helper.setText(html, true);

      mailSender.send(mimeMessage);
    } catch (MessagingException | IOException | MailException e) {
      throw new IllegalStateException("Failed to send verification email", e);
    }
  }

  public void sendEmailChangeMail(String username, String email, String verificationToken) {
    String verificationUrl = UriComponentsBuilder
            .fromUriString(frontendUrl)
            .path(frontendPathVerifyEmail)
            .queryParam("token", verificationToken)
            .toUriString();

    try {
      String html = loadChangeEmailTemplate(username, verificationUrl);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

      helper.setFrom(from);
      helper.setTo(email);
      helper.setSubject("Verify Your email");
      helper.setText(html, true);

      mailSender.send(mimeMessage);
    } catch (MessagingException | IOException | MailException e) {
      throw new IllegalStateException("Failed to send verification email", e);
    }
  }

  public void sendPasswordReset(String email, String passwordResetToken) {
    String verificationUrl = UriComponentsBuilder
            .fromUriString(frontendUrl)
            .path(frontendPathResetPassword)
            .queryParam("token", passwordResetToken)
            .toUriString();

    try {
      String html = loadForgotPasswordTemplate(verificationUrl);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

      helper.setFrom(from);
      helper.setTo(email);
      helper.setSubject("Reset Your password");
      helper.setText(html, true);

      mailSender.send(mimeMessage);
    } catch (MessagingException | IOException | MailException e) {
      throw new IllegalStateException("Failed to send verification email", e);
    }
  }

  private String loadVerificationTemplate(String username, String verificationUrl) throws IOException {
    String template = StreamUtils.copyToString(
            verificationEmailTemplate.getInputStream(),
            StandardCharsets.UTF_8
    );

    return template
            .replace("{{username}}", HtmlUtils.htmlEscape(username))
            .replace("{{verificationUrl}}", HtmlUtils.htmlEscape(verificationUrl));
  }

  private String loadChangeEmailTemplate(String username, String verificationUrl) throws IOException {
    String template = StreamUtils.copyToString(
            changeEmailTemplate.getInputStream(),
            StandardCharsets.UTF_8
    );

    return template
            .replace("{{username}}", HtmlUtils.htmlEscape(username))
            .replace("{{verificationUrl}}", HtmlUtils.htmlEscape(verificationUrl));
  }

  private String loadForgotPasswordTemplate(String passwordResetUrl) throws IOException {
    String template = StreamUtils.copyToString(
            resetPasswordTemplate.getInputStream(),
            StandardCharsets.UTF_8
    );

    return template
            .replace("{{passwordResetUrl}}", HtmlUtils.htmlEscape(passwordResetUrl));
  }
}

