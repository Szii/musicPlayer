package org.dnd.board;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dnd.group.GroupEntity;
import org.dnd.session.SessionEntity;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackWindowEntity;
import org.dnd.user.UserEntity;

import java.util.UUID;

@Entity
@Table(name = "boards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoardEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "selected_track_id")
  private TrackEntity selectedTrack;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "selected_group_id")
  private GroupEntity selectedGroup;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "selected_window_id")
  private TrackWindowEntity selectedWindow;

  @Column(nullable = false)
  private int volume = 50;

  @Column(nullable = false)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private UserEntity owner;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false)
  private SessionEntity session;

  @Column(nullable = false)
  private boolean repeat = false;

  @Column(nullable = false)
  private boolean overplay = false;

  @Column(nullable = false)
  private boolean shuffle = false;

  @Column(nullable = false)
  private boolean playlistMode = false;

  @Column(nullable = false)
  private boolean sequenceMode = false;
}

