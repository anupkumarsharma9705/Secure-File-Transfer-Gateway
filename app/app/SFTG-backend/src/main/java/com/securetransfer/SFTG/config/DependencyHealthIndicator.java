package com.securetransfer.SFTG.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Readiness checks for external dependencies:
 *  - database:   a real connection + isValid() against the pool
 *  - virustotal: configuration status only (no API call — probing on every
 *                readiness check would burn the free-tier request quota)
 *
 * Exposed at /actuator/health/readiness (probes enabled in application.properties).
 * Both checks degrade to DOWN without throwing — the health endpoint never 500s.
 */
@Component("dependencies")
public class DependencyHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Value("${virustotal.api.key:}")
    private String virusTotalApiKey;

    public DependencyHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                builder.down().withDetail("database", "connection invalid");
            } else {
                builder.withDetail("database", "up");
            }
        } catch (SQLException ex) {
            builder.down().withDetail("database", "unreachable: " + ex.getMessage());
        }

        // VirusTotal outages are non-blocking by design (ADR-002), so this is a
        // configuration signal rather than a liveness probe.
        builder.withDetail("virustotal",
                virusTotalApiKey == null || virusTotalApiKey.isBlank()
                        ? "not configured (scans skipped)"
                        : "configured");

        return builder.build();
    }
}
