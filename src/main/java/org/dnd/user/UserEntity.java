package org.dnd.user;

import jakarta.persistence.*;
import lombok.*;
import org.dnd.board.BoardEntity;
import org.dnd.group.GroupEntity;
import org.dnd.track.TrackEntity;
import org.dnd.track.trackShare.TrackShareEntity;

import java.util.HashSet;
import java.util.Set;

@Builder
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(nullable = false)
  private String password;

  @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<TrackEntity> ownedTracks = new HashSet<>();

  @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<GroupEntity> ownedGroups = new HashSet<>();

  @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<BoardEntity> boards = new HashSet<>();

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private UserRank rank = UserRank.NORMAL;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
          name = "user_shares",
          joinColumns = @JoinColumn(name = "user_id"),
          inverseJoinColumns = @JoinColumn(name = "share_id")
  )
  private Set<TrackShareEntity> shares = new HashSet<>();
}
