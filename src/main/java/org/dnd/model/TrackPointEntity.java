package org.dnd.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "track_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackPointEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private TrackEntity track;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long position;

    @Column(nullable = false)
    private boolean fadeIn;

    @Column(nullable = false)
    private boolean fadeOut;

}
