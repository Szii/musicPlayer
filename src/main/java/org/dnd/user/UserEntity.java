package org.dnd.user;

import jakarta.persistence.*;
import lombok.*;
import org.dnd.board.BoardEntity;
import org.dnd.group.GroupEntity;
import org.dnd.token.TokenEntity;
import org.dnd.track.TrackEntity;
import org.dnd.track.trackShare.TrackShareEntity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Builder
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column
  private String password;

  //keep nullable due to existing users
  @Column(name = "keycloak_id", unique = true)
  private UUID keycloakId;

  @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<TrackEntity> ownedTracks = new HashSet<>();

  @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<GroupEntity> ownedGroups = new HashSet<>();

  @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<BoardEntity> boards = new HashSet<>();

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private UserRank rank = UserRank.NORMAL;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "email_verified", nullable = false)
  @Builder.Default
  private boolean emailVerified = false;

  @Column(name = "pending_email", unique = true)
  private String pendingEmail;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
          name = "user_shares",
          joinColumns = @JoinColumn(name = "user_id"),
          inverseJoinColumns = @JoinColumn(name = "share_id")
  )

  @Builder.Default
  private Set<TrackShareEntity> shares = new HashSet<>();

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<TokenEntity> tokens = new HashSet<>();

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }

  public void addShare(TrackShareEntity share) {
    this.shares.add(share);
    share.getUsers().add(this);
  }
}