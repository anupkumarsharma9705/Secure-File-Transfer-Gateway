package com.securetransfer.SFTG.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DependencyHealthIndicatorTest {

    @Test
    void reportsUpWithDatabaseDetailWhenReachable() throws Exception {
        DependencyHealthIndicator indicator = indicatorWithConnection(true);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("up", health.getDetails().get("database"));
    }

    @Test
    void reportsDownWhenConnectionIsInvalid() throws Exception {
        DependencyHealthIndicator indicator = indicatorWithConnection(false);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("connection invalid", health.getDetails().get("database"));
    }

    @Test
    void reportsDownWhenDatabaseThrows() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("db down"));

        DependencyHealthIndicator indicator = new DependencyHealthIndicator(dataSource);
        configureVirusTotal(indicator, "test-key");

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(String.valueOf(health.getDetails().get("database")).startsWith("unreachable"));
    }

    @Test
    void reportsVirusTotalConfiguredWhenKeyPresent() throws Exception {
        DependencyHealthIndicator indicator = indicatorWithConnection(true);
        configureVirusTotal(indicator, "test-key");

        Health health = indicator.health();

        assertEquals("configured", health.getDetails().get("virustotal"));
    }

    @Test
    void reportsVirusTotalSkippedWhenKeyMissing() throws Exception {
        DependencyHealthIndicator indicator = indicatorWithConnection(true);
        configureVirusTotal(indicator, "");

        Health health = indicator.health();

        assertTrue(String.valueOf(health.getDetails().get("virustotal")).startsWith("not configured"));
    }

    private DependencyHealthIndicator indicatorWithConnection(boolean valid) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(valid);

        return new DependencyHealthIndicator(dataSource);
    }

    private void configureVirusTotal(DependencyHealthIndicator indicator, String apiKey) {
        ReflectionTestUtils.setField(indicator, "virusTotalApiKey", apiKey);
    }
}
