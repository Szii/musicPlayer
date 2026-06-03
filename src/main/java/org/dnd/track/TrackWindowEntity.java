package org.dnd.track;

import jakarta.persistence.*;
import lombok.*;

@Builder
@Entity
@Table(name = "track_windows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackWindowEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "track_id")
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
}
