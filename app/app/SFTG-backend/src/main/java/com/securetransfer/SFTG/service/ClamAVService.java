package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;

@Service
@Slf4j
public class ClamAVService {

    // ✅ Read from properties so you never hardcode host again
    @Value("${clamav.host:127.0.0.1}")
    private String clamavHost;

    @Value("${clamav.port:3310}")
    private int clamavPort;

    // How long to wait for ClamAV to respond (ms)
    private static final int SOCKET_TIMEOUT_MS = 10000; // 10 seconds

    public void scanFile(Path filePath) {

        log.info("[CLAMAV] Starting virus scan for: {}", filePath.getFileName());

        try (Socket socket = new Socket()) {

            // ✅ Connect with timeout — don't hang forever if ClamAV is slow
            socket.connect(
                    new java.net.InetSocketAddress(clamavHost, clamavPort),
                    SOCKET_TIMEOUT_MS
            );
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Send INSTREAM command — null-terminated
            out.write("zINSTREAM\0".getBytes());
            out.flush();

            // Stream the file in chunks with 4-byte length prefix per ClamAV protocol
            try (InputStream fis = new FileInputStream(filePath.toFile())) {
                byte[] buffer = new byte[4096]; // ✅ 4096 is better than 2048
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    // ClamAV INSTREAM protocol: send chunk length as 4-byte big-endian
                    out.write(new byte[]{
                            (byte) (bytesRead >> 24),
                            (byte) (bytesRead >> 16),
                            (byte) (bytesRead >> 8),
                            (byte) bytesRead
                    });
                    out.write(buffer, 0, bytesRead);
                }
            }

            // Send zero-length chunk to signal end of stream
            out.write(new byte[]{0, 0, 0, 0});
            out.flush();

            // Read ClamAV response
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String response = reader.readLine();

            log.info("[CLAMAV] Response: {}", response);

            if (response == null) {
                // ClamAV closed connection without responding — treat as scan failure
                throw new FileStorageException(
                        "Virus scan returned no response. Upload rejected for safety."
                );
            }

            if (response.contains("FOUND")) {
                // Extract the virus name from response for better logging
                // Response format: "stream: VirusName FOUND"
                log.warn("[CLAMAV] Malware detected in file: {} — Response: {}",
                        filePath.getFileName(), response);
                throw new FileStorageException(
                        "Malicious file detected. Upload rejected."
                );
            }

            if (response.contains("ERROR")) {
                log.error("[CLAMAV] Scan error response: {}", response);
                throw new FileStorageException(
                        "Virus scan encountered an error. Upload rejected for safety."
                );
            }

            // "stream: OK" means clean
            log.info("[CLAMAV] File is clean: {}", filePath.getFileName());

        } catch (ConnectException e) {
            // ✅ Specific catch — connection refused means ClamAV is not reachable
            log.error("[CLAMAV] Cannot connect to ClamAV at {}:{} — {}",
                    clamavHost, clamavPort, e.getMessage());
            throw new FileStorageException(
                    "Virus scanner is unavailable. Please try again later."
            );

        } catch (SocketTimeoutException e) {
            log.error("[CLAMAV] Connection timed out to {}:{}", clamavHost, clamavPort);
            throw new FileStorageException(
                    "Virus scan timed out. Please try again."
            );

        } catch (FileStorageException e) {
            // Re-throw our own exceptions without wrapping them
            throw e;

        } catch (IOException e) {
            log.error("[CLAMAV] IO error during virus scan: {}", e.getMessage());
            throw new FileStorageException(
                    "Virus scan failed due to an IO error. Upload rejected.", e
            );
        }
    }
}