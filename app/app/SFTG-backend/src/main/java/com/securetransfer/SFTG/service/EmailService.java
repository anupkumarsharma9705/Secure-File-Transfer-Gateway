package com.securetransfer.SFTG.service;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${brevo.sender.email:mr.machine9705@gmail.com}")
    private String senderEmail;

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    // One client per bean: OkHttp reuses connections and thread pools.
    private final OkHttpClient client = new OkHttpClient();

    public void sendOtp(String toEmail, String otp) {

        String jsonBody = """
    {
      "sender": {
        "email": "%s",
        "name": "SFTG"
      },
      "to": [ { "email": "%s" } ],
      "subject": "SFTG Email Verification OTP",
      "textContent": "Your OTP is: %s\\nValid for 5 minutes.\\n\\nSFTG Team"
    }
    """.formatted(senderEmail, toEmail, otp);

        RequestBody body = RequestBody.create(
                jsonBody, MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(BREVO_URL)
                .post(body)
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .addHeader("api-key", brevoApiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {

            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("Brevo rejected OTP email: status={} body={}", response.code(), responseBody);
                throw new RuntimeException("Brevo Error: " + responseBody);
            }

            log.info("OTP email accepted by Brevo for {}", toEmail);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OTP email failed", e);
        }
    }
}
