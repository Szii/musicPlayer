package org.dnd.track;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Builder
@Entity
@Table(
        name = "track_windows",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_track_window_position",
                        columnNames = {"track_id", "position_within_track"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackWindowEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "track_id", nullable = false)
  private TrackEntity track;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private Long positionFrom;

  @Column(nullable = false)
  private Long positionTo;

  @Column(nullable = false)
  private int fadeInDurationMs;

  @Column(nullable = false)
  private int fadeOutDurationMs;

  @Column(name = "position_within_track", nullable = false)
  private int positionWithinTrack;
}