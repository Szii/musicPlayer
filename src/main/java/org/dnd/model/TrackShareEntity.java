package org.dnd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "track_shares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackShareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String description;

    @Column(nullable = false)
    private String shareCode;

    @OneToOne(mappedBy = "trackShare")
    private TrackEntity track;
}

