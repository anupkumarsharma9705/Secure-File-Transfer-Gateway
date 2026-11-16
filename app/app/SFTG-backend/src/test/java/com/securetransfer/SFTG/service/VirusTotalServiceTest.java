package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.exception.FileStorageException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the VirusTotal v3 client against a local JDK HTTP server.
 * No network access and no API quota are consumed.
 */
class VirusTotalServiceTest {

    @TempDir
    Path tempDir;

    private final AtomicInteger uploads = new AtomicInteger();
    private HttpServer server;
    private int fileReportStatus;
    private String fileReportBody;
    private String analysisBody;

    @BeforeEach
    void startServer() throws IOException {
        uploads.set(0);
        fileReportStatus = 200;
        fileReportBody = "{\"data\":{\"attributes\":{\"last_analysis_stats\":"
                + "{\"malicious\":0,\"suspicious\":0}}}}";
        analysisBody = "{\"data\":{\"attributes\":{\"status\":\"completed\","
                + "\"stats\":{\"malicious\":0,\"suspicious\":0}}}}";

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            int status = 200;
            String body;
            if (path.startsWith("/api/files/") && "GET".equals(method)) {
                status = fileReportStatus;
                body = status == 200 ? fileReportBody : "{\"error\":{\"code\":\"NotFoundError\"}}";
            } else if (path.equals("/api/files") && "POST".equals(method)) {
                uploads.incrementAndGet();
                body = "{\"data\":{\"type\":\"analysis\",\"id\":\"analysis-1\"}}";
            } else if (path.equals("/api/analyses/analysis-1")) {
                body = analysisBody;
            } else {
                status = 404;
                body = "{}";
            }

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void knownCleanFileIsAcceptedWithoutUpload() throws Exception {
        VirusTotalService service = service();

        assertDoesNotThrow(() -> service.scanFile(file("clean.txt")));
        assertEquals(0, uploads.get());
    }

    @Test
    void knownMaliciousFileIsRejectedWithoutUpload() throws Exception {
        fileReportBody = "{\"data\":{\"attributes\":{\"last_analysis_stats\":"
                + "{\"malicious\":3,\"suspicious\":1}}}}";
        VirusTotalService service = service();

        assertThrows(FileStorageException.class, () -> service.scanFile(file("bad.txt")));
        assertEquals(0, uploads.get());
    }

    @Test
    void unknownFileIsUploadedAndRejectedWhenAnalysisFindsMalware() throws Exception {
        fileReportStatus = 404;
        analysisBody = "{\"data\":{\"attributes\":{\"status\":\"completed\","
                + "\"stats\":{\"malicious\":1,\"suspicious\":0}}}}";
        VirusTotalService service = service();

        assertThrows(FileStorageException.class,
                () -> service.scanFile(file("unknown-bad.txt")));
        assertEquals(1, uploads.get());
    }

    @Test
    void unknownFileIsAcceptedWhenAnalysisIsClean() throws Exception {
        fileReportStatus = 404;
        VirusTotalService service = service();

        assertDoesNotThrow(() -> service.scanFile(file("unknown-clean.txt")));
        assertEquals(1, uploads.get());
    }

    @Test
    void missingApiKeySkipsScanAndAllowsUpload() throws Exception {
        VirusTotalService service = service();
        ReflectionTestUtils.setField(service, "apiKey", "");

        assertDoesNotThrow(() -> service.scanFile(file("offline.txt")));
        assertEquals(0, uploads.get());
    }

    @Test
    void apiErrorDoesNotBlockUpload() throws Exception {
        fileReportStatus = 500;
        VirusTotalService service = service();

        assertDoesNotThrow(() -> service.scanFile(file("outage.txt")));
    }

    @Test
    void unknownFileIsAllowedWhenUploadsDisabled() throws Exception {
        fileReportStatus = 404;
        VirusTotalService service = service();
        ReflectionTestUtils.setField(service, "uploadUnknown", false);

        assertDoesNotThrow(() -> service.scanFile(file("no-upload.txt")));
        assertEquals(0, uploads.get());
    }

    @Test
    void unfinishedAnalysisDoesNotBlockUpload() throws Exception {
        fileReportStatus = 404;
        analysisBody = "{\"data\":{\"attributes\":{\"status\":\"queued\","
                + "\"stats\":{\"malicious\":0}}}}";
        VirusTotalService service = service();

        assertDoesNotThrow(() -> service.scanFile(file("queued.txt")));
        assertEquals(1, uploads.get());
    }

    private VirusTotalService service() {
        VirusTotalService service = new VirusTotalService();
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
        ReflectionTestUtils.setField(service, "uploadUnknown", true);
        ReflectionTestUtils.setField(service, "pollAttempts", 1);
        ReflectionTestUtils.setField(service, "pollIntervalMs", 0L);
        return service;
    }

    private Path file(String name) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, "content of " + name);
        return path;
    }
}
