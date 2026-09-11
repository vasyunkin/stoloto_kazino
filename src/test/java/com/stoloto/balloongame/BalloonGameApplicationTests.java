package com.stoloto.balloongame;

import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.Player;
import com.stoloto.balloongame.domain.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S1 integration test — requires Docker.
 *
 * @Testcontainers(disabledWithoutDocker = true):
 *   SKIPPED (not failed) when Docker daemon is not reachable.
 *   This makes the build green on machines without Docker or with socket issues,
 *   while still running the full test when Docker is available.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class BalloonGameApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private GameConfig gameConfig;

    @Test
    void contextLoads() {
        // Passes if Spring context starts without errors (Flyway + JPA validation included).
    }

    @Test
    void flywayMigration_allTablesExist() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            for (String table : new String[]{"player", "game_round", "wallet_ledger", "config_snapshot"}) {
                ResultSet rs = conn.getMetaData().getTables(null, null, table, new String[]{"TABLE"});
                assertThat(rs.next())
                        .as("Table '%s' must exist after Flyway V1 migration", table)
                        .isTrue();
            }
        }
    }

    @Test
    void gameConfig_boundFromYaml() {
        assertThat(gameConfig.getGreenLevels()).isEqualTo(9);
        assertThat(gameConfig.getRedLevels()).isEqualTo(12);
        assertThat(gameConfig.getMath().getGrowthRate()).isEqualTo(0.065);
        assertThat(gameConfig.getBoosters().getSpawnProbability()).isEqualTo(0.70);
        assertThat(gameConfig.getBoosters().getTiers()).hasSize(3);
    }

    @Test
    void playerRepository_saveAndFind() {
        long countBefore = playerRepository.count();
        Player p = new Player("test-user-s1-" + System.nanoTime());
        playerRepository.saveAndFlush(p);
        assertThat(playerRepository.count()).isEqualTo(countBefore + 1);
    }
}
