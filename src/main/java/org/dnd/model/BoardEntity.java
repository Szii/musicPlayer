package org.dnd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "boards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoardEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_track_id")
    private TrackEntity selectedTrack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_group_id")
    private GroupEntity selectedGroup;

    @Column(nullable = false)
    private int volume = 50;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    @Column(nullable = false)
    private boolean repeat = false;

    @Column(nullable = false)
    private boolean overplay = false;
}

