package org.dnd.session;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dnd.board.BoardEntity;
import org.dnd.group.GroupEntity;
import org.dnd.session.share.SessionShareEntity;
import org.dnd.track.TrackEntity;
import org.dnd.user.UserEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

//Groups for boards
@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column
  private String description;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private UserEntity owner;

  @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("name ASC")
  private Set<BoardEntity> boards = new HashSet<>();

  @ManyToMany
  @JoinTable(
          name = "session_tracks",
          joinColumns = @JoinColumn(name = "session_id"),
          inverseJoinColumns = @JoinColumn(name = "track_id")
  )
  private Set<TrackEntity> tracks = new HashSet<>();

  @ManyToMany
  @JoinTable(
          name = "session_groups",
          joinColumns = @JoinColumn(name = "session_id"),
          inverseJoinColumns = @JoinColumn(name = "group_id")
  )
  private Set<GroupEntity> groups = new HashSet<>();

  @Column(nullable = false)
  private boolean subscribed = false;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_share_id")
  private SessionShareEntity sourceShare;

  @Column(name = "installed_version")
  private Integer installedVersion;

  @Column(nullable = false)
  private boolean modified = false;
}
