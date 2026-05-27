package org.dnd.email;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  @Value("${app.frontend-path}")
  private String frontendPath;

  @Value("${app.mail.from}")
  private String from;

  public void sendVerificationEmail(String username, String email, String verificationToken) {
    String verificationUrl = UriComponentsBuilder
            .fromUriString(frontendUrl)
            .path(frontendPath)
            .queryParam("token", verificationToken)
            .toUriString();

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject("Verify your email");
    message.setText("""
            Hello, %s
            
            Please verify your email by clicking this link:
            
            %s
            
            If you did not create an account, you can ignore this email.
            
            Do not reply to this email.
            """.formatted(username, verificationUrl));

    mailSender.send(message);
  }
}
