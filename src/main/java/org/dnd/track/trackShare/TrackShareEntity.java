package org.dnd.track.trackShare;

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
@Table(name = "track_shares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackShareEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column
  private String description;

  @Column(nullable = false)
  private String shareCode;

  @OneToOne(mappedBy = "trackShare")
  private TrackEntity track;

  @ManyToMany(mappedBy = "shares")
  private Set<UserEntity> users = new HashSet<>();
}

