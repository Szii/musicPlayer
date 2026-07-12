package org.dnd.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.dnd.exception.EmailDeliveryException;
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
import java.util.Map;

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

  @Value("classpath:email/email-change-notice.html")
  private Resource emailChangeNoticeTemplate;

  public void sendVerificationEmail(String username, String email, String verificationToken) {
    send(email, "Verify Your email", verificationEmailTemplate, Map.of(
            "username", username,
            "verificationUrl", frontendLink(frontendPathVerifyEmail, verificationToken)
    ));
  }

  public void sendEmailChangeMail(String username, String email, String verificationToken) {
    send(email, "Verify Your email", changeEmailTemplate, Map.of(
            "username", username,
            "verificationUrl", frontendLink(frontendPathVerifyEmail, verificationToken)
    ));
  }

  public void sendPasswordReset(String email, String passwordResetToken) {
    send(email, "Reset Your password", resetPasswordTemplate, Map.of(
            "passwordResetUrl", frontendLink(frontendPathResetPassword, passwordResetToken)
    ));
  }

  public void sendEmailChangeNotice(String username, String previousEmail, String newEmail) {
    send(previousEmail, "Your email address is being changed", emailChangeNoticeTemplate, Map.of(
            "username", username,
            "newEmail", newEmail
    ));
  }

  private void send(String email, String subject, Resource template, Map<String, String> placeholders) {
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

      helper.setFrom(from);
      helper.setTo(email);
      helper.setSubject(subject);
      helper.setText(render(template, placeholders), true);

      mailSender.send(mimeMessage);
    } catch (MessagingException | IOException | MailException e) {
      throw new EmailDeliveryException("Failed to send email to " + email, e);
    }
  }

  private String render(Resource template, Map<String, String> placeholders) throws IOException {
    String html = StreamUtils.copyToString(
            template.getInputStream(),
            StandardCharsets.UTF_8
    );

    for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
      html = html.replace(
              "{{" + placeholder.getKey() + "}}",
              HtmlUtils.htmlEscape(placeholder.getValue())
      );
    }

    return html;
  }

  private String frontendLink(String path, String token) {
    return UriComponentsBuilder
            .fromUriString(frontendUrl)
            .path(path)
            .queryParam("token", token)
            .toUriString();
  }
}
