package org.dnd.group;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dnd.track.TrackEntity;
import org.dnd.track.TrackWindowEntity;
import org.dnd.user.UserEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "list_name", nullable = false)
  private String listName;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private UserEntity owner;

  @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("positionWithinGroup ASC")
  private Set<GroupTrackEntity> groupTracks = new HashSet<>();

  public GroupTrackEntity addTrack(TrackEntity track, TrackWindowEntity trackWindow, String customName) {
    GroupTrackEntity groupTrack = new GroupTrackEntity(this, track, trackWindow, customName);
    groupTracks.add(groupTrack);
    return groupTrack;
  }

  public GroupTrackEntity addTrack(TrackEntity track, String customName) {
    return addTrack(track, null, customName);
  }

  public GroupTrackEntity addTrack(TrackEntity track) {
    return addTrack(track, null, null);
  }

  public void removeTrack(UUID trackId) {
    groupTracks.removeIf(groupTrack -> groupTrack.getTrack().getId().equals(trackId));
  }
}