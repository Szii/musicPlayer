package org.dnd.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dnd.track.TrackEntity;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "group_tracks",
        uniqueConstraints = @UniqueConstraint(name = "uq_group_track", columnNames = {"group_id", "track_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class GroupTrackEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private GroupEntity group;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "track_id", nullable = false)
  private TrackEntity track;

  @Column(name = "custom_name")
  private String customName;

  public GroupTrackEntity(GroupEntity group, TrackEntity track, String customName) {
    this.group = group;
    this.track = track;
    this.customName = customName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GroupTrackEntity that)) {
      return false;
    }
    return Objects.equals(group, that.group) && Objects.equals(track, that.track);
  }

  @Override
  public int hashCode() {
    return Objects.hash(group, track);
  }
}
