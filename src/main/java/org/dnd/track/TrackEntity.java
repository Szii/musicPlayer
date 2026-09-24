package org.dnd.track;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dnd.group.GroupTrackEntity;
import org.dnd.session.SessionEntity;
import org.dnd.user.UserEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tracks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "track_name", nullable = false)
  private String trackName;

  @Column(name = "track_original_name", nullable = false)
  private String trackOriginalName;

  @Column(name = "track_link", nullable = false)
  private String trackLink;

  @Column(nullable = false)
  private int duration;

  @Column(nullable = false)
  private int fadeInDurationMs;

  @Column(nullable = false)
  private int fadeOutDurationMs;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private UserEntity owner;

  @OneToMany(mappedBy = "track")
  private Set<GroupTrackEntity> groupTracks = new HashSet<>();

  @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("positionWithinTrack  ASC, name ASC")
  private List<TrackWindowEntity> trackWindows = new ArrayList<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "managed_session_id")
  private SessionEntity managedSession;

  public boolean isManaged() {
    return managedSession != null;
  }

  public void addTrackWindow(TrackWindowEntity window) {
    this.trackWindows.add(window);
    window.setTrack(this);
  }
}



