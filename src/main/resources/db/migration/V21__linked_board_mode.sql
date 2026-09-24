ALTER TABLE boards
    ADD COLUMN linked_board_mode VARCHAR(16);

UPDATE boards
SET linked_board_mode = 'START'
WHERE linked_board_id IS NOT NULL;
