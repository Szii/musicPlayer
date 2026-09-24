package org.dnd.board;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LinkedBoard {
  @Column(name = "linked_board_id")
  private UUID boardId;

  @Column(name = "linked_board_mode")
  @Enumerated(EnumType.STRING)
  private LinkedBoardMode mode;
}
