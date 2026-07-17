package org.dnd.track;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dnd.group.GroupEntity;
import org.dnd.track.trackShare.TrackShareEntity;
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

  @ManyToMany(mappedBy = "tracks")
  private Set<GroupEntity> groups = new HashSet<>();

  @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("positionWithinTrack  ASC, name ASC")
  private List<TrackWindowEntity> trackWindows = new ArrayList<>();

  @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "track_share_id", unique = true)
  private TrackShareEntity trackShare;

  public void addTrackWindow(TrackWindowEntity window) {
    this.trackWindows.add(window);
    window.setTrack(this);
  }
}



