package cometfit.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import cometfit.api.ApiException;

/**
 * Authenticates calls from external systems that have no user session: the UREC card reader (UC-2, SD-2
 * "recordVisit(studentId, time)") and the monitoring service (UC-4, SD-4 "healthCheck(servers)").
 * Header "X-Integration-Key" carrying a shared secret (A-12). Part of the Auth Gateway (§5.3.2).
 */
@Component
public class IntegrationKeyVerifier {
    public static final String HEADER = "X-Integration-Key";
    private final byte[] key;

    public IntegrationKeyVerifier(@Value("${cometfit.integration-key}") String key) {
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String presented) {
        if (presented == null || !MessageDigest.isEqual(key, presented.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.unauthorized("Invalid integration key");
        }
    }
}
