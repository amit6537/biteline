package com.fooddelivery.backend;

import com.fooddelivery.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: the whole app starts against real Postgres/Redis/Kafka
 * (Testcontainers), Flyway applies every migration, and /actuator/health
 * answers. If this fails, nothing else can pass.
 */
class BackendApplicationTests extends AbstractIntegrationTest {

    @Test
    void appStartsAndAllMigrationsAreApplied() {
        assertThat(api.get("/actuator/health").status()).isEqualTo(200);

        Integer latest = jdbc.queryForObject(
                "SELECT MAX(CAST(version AS INT)) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(latest).isEqualTo(7);
    }
}
