package org.dnd.email;

import jakarta.persistence.*;
import lombok.*;
import org.dnd.user.UserEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 255)
  private String token;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private UserEntity user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private EmailVerificationTokenType type;

  @Column(name = "target_email", nullable = false)
  private String targetEmail;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "is_valid", nullable = false)
  @Builder.Default
  private boolean valid = true;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}