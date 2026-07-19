package org.dnd.group;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dnd.track.TrackEntity;
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
  private Set<GroupTrackEntity> groupTracks = new HashSet<>();

  public void addTrack(TrackEntity track, String customName) {
    groupTracks.add(new GroupTrackEntity(this, track, customName));
  }

  public void addTrack(TrackEntity track) {
    addTrack(track, null);
  }

  public void removeTrack(UUID trackId) {
    groupTracks.removeIf(groupTrack -> groupTrack.getTrack().getId().equals(trackId));
  }
}