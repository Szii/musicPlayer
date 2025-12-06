package org.dnd.repository;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;


@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DatabaseBase {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("musicplayer")
                    .withUsername("musicuser")
                    .withPassword("musicpass");
}
