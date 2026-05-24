package org.dnd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DatabaseBase {

  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
          new PostgreSQLContainer<>("postgres:16-alpine")
                  .withDatabaseName("musicplayer")
                  .withUsername("musicuser")
                  .withPassword("musicpass");

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    List<String> tables = jdbcTemplate.queryForList("""
            SELECT tablename
            FROM pg_tables
            WHERE schemaname = 'public'
              AND tablename <> 'flyway_schema_history'
            """, String.class);

    if (tables.isEmpty()) {
      return;
    }

    String tableNames = tables.stream()
            .map(table -> "\"" + table + "\"")
            .collect(Collectors.joining(", "));

    jdbcTemplate.execute("TRUNCATE TABLE " + tableNames + " RESTART IDENTITY CASCADE");
  }
}
