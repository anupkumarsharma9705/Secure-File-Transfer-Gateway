package com.securetransfer.SFTG.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetransfer.SFTG.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * Malware scanning through the VirusTotal v3 API.
 *
 * Chosen over a local ClamAV daemon because a ClamAV container needs roughly
 * 1 GB of RAM for its signature database, which does not fit small/free cloud
 * instances. VirusTotal moves that cost off-box.
 *
 * Flow (cheapest first, because the free API allows ~4 requests/minute):
 *   1. SHA-256 the file and look the hash up — no file leaves the server.
 *   2. If the hash is known, accept/reject immediately from its report.
 *   3. Only if unknown, upload it (files >32 MB go through a one-time upload URL)
 *      and poll the analysis a configurable number of times.
 *
 * Failure policy follows ADR-002: a scanner outage or quota error never blocks
 * uploads; only a confirmed malicious verdict does. Scans are skipped entirely
 * when no API key is configured, so local development works offline.
 */
@Service
@Slf4j
public class VirusTotalService {

    private static final long MAX_DIRECT_UPLOAD_BYTES = 32L * 1024 * 1024;
    private static final MediaType OCTET_STREAM = MediaType.get("application/octet-stream");

    @Value("${virustotal.api.key:}")
    private String apiKey;

    @Value("${virustotal.base-url:https://www.virustotal.com/api/v3}")
    private String baseUrl;

    @Value("${virustotal.upload-unknown:true}")
    private boolean uploadUnknown;

    @Value("${virustotal.poll-attempts:2}")
    private int pollAttempts;

    @Value("${virustotal.poll-interval-ms:20000}")
    private long pollIntervalMs;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public void scanFile(Path filePath) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[VIRUSTOTAL] API key not configured — skipping scan for {}",
                    filePath.getFileName());
            return;
        }

        try {
            String hash = sha256(filePath);
            JsonNode report = get("/files/" + hash);

            if (report != null) {
                evaluate(report.path("data").path("attributes").path("last_analysis_stats"), filePath);
                return;
            }

            if (!uploadUnknown) {
                log.warn("[VIRUSTOTAL] {} is unknown to VirusTotal and uploads are disabled — allowing upload",
                        filePath.getFileName());
                return;
            }

            String analysisId = upload(filePath);
            pollAnalysis(analysisId, filePath);

        } catch (FileStorageException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[VIRUSTOTAL] Scan interrupted for {} — allowing upload", filePath.getFileName());
        } catch (Exception e) {
            // Non-blocking by design: a third-party outage must not stop uploads.
            log.error("[VIRUSTOTAL] Scan unavailable for {}: {}", filePath.getFileName(), e.getMessage());
        }
    }

    private void pollAnalysis(String analysisId, Path filePath) throws IOException, InterruptedException {
        int attempts = Math.max(1, pollAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (pollIntervalMs > 0) {
                TimeUnit.MILLISECONDS.sleep(pollIntervalMs);
            }
            JsonNode report = get("/analyses/" + analysisId);
            if (report == null) {
                continue;
            }
            JsonNode attributes = report.path("data").path("attributes");
            if ("completed".equals(attributes.path("status").asText())) {
                evaluate(attributes.path("stats"), filePath);
                return;
            }
        }
        log.warn("[VIRUSTOTAL] Analysis for {} not completed after {} poll(s) — allowing upload",
                filePath.getFileName(), attempts);
    }

    private String upload(Path filePath) throws IOException {
        String url = url("/files");

        if (Files.size(filePath) > MAX_DIRECT_UPLOAD_BYTES) {
            JsonNode uploadUrl = get("/files/upload_url");
            if (uploadUrl == null || uploadUrl.path("data").asText("").isBlank()) {
                throw new IOException("VirusTotal did not return a large-file upload URL");
            }
            url = uploadUrl.path("data").asText();
        }

        RequestBody fileBody = RequestBody.create(OCTET_STREAM, filePath.toFile());
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filePath.getFileName().toString(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("x-apikey", apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("VirusTotal upload returned " + response.code());
            }
            JsonNode json = parse(response);
            String analysisId = json.path("data").path("id").asText("");
            if (analysisId.isBlank()) {
                throw new IOException("VirusTotal upload response did not contain an analysis id");
            }
            return analysisId;
        }
    }

    /**
     * @return the report body, or null when the hash is not in VirusTotal.
     */
    private JsonNode get(String path) throws IOException {
        Request request = new Request.Builder()
                .url(url(path))
                .get()
                .addHeader("x-apikey", apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 404) {
                return null;
            }
            if (!response.isSuccessful()) {
                throw new IOException("VirusTotal GET " + path + " returned " + response.code());
            }
            return parse(response);
        }
    }

    private JsonNode parse(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? mapper.createObjectNode() : mapper.readTree(body.string());
    }

    private void evaluate(JsonNode stats, Path filePath) {
        int malicious = stats.path("malicious").asInt(0);
        int suspicious = stats.path("suspicious").asInt(0);

        if (malicious > 0) {
            log.warn("[VIRUSTOTAL] Malware detected in {} (malicious={}, suspicious={})",
                    filePath.getFileName(), malicious, suspicious);
            throw new FileStorageException("Malicious file detected. Upload rejected.");
        }

        log.info("[VIRUSTOTAL] {} is clean (malicious=0, suspicious={})",
                filePath.getFileName(), suspicious);
    }

    private String url(String path) {
        return baseUrl.replaceAll("/+$", "") + path;
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
