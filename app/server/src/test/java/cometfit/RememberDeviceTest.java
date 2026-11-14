package cometfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import cometfit.security.Totp;
import cometfit.security.TrustedDeviceRepository;

/**
 * TD-1 (3) "Remember this device for 30 days. On a remembered device the student signs in with username and password
 * only; the 6-digit code is required again on a new device or after 30 days." Still NFR Security (password always,
 * TOTP at least every 30 days per device). TD-1 (1) setup data for "Add to authenticator app" / "Copy setup key".
 */
class RememberDeviceTest extends ApiTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired TrustedDeviceRepository trusted;

    private JsonNode loginRaw(String username, String password, String code, Boolean remember, String deviceToken,
                              int status) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("username", username);
        b.put("password", password);
        if (code != null) b.put("totpCode", code);
        if (remember != null) b.put("rememberDevice", remember);
        if (deviceToken != null) b.put("deviceToken", deviceToken);
        return call(post("/api/auth/login"), null, b, status);
    }

    private String code(Account a) { return Totp.codeAt(a.secret(), Instant.now()); }

    @Test
    void registrationReturnsTheSetupKeyAndAnAuthenticatorLink() throws Exception {
        Account a = newStudent("setup");
        // newStudent already used totpSecret; check the otpauth link format the client opens / encodes as QR
        JsonNode reg = call(post("/api/auth/register"), null, Map.of("username", "setup_" + System.nanoTime() % 100000,
                "password", PASSWORD, "email", "s" + System.nanoTime() % 100000 + "@test.local"), 201);
        String uri = reg.get("otpauthUri").asText();
        assertThat(uri).startsWith("otpauth://totp/CometFit:").contains("secret=" + reg.get("totpSecret").asText())
                .contains("issuer=CometFit");
        assertThat(a.secret()).isNotBlank();
    }

    @Test
    void codeIsStillRequiredWithoutARememberedDevice() throws Exception {
        Account a = newStudent("nocode");
        JsonNode r = loginRaw(a.username(), PASSWORD, null, null, null, 400);
        assertThat(r.get("message").asText()).contains("6-digit code");
        loginRaw(a.username(), "wrong-password", null, null, null, 400);   // same answer: no password oracle
        JsonNode ok = loginRaw(a.username(), PASSWORD, code(a), null, null, 200);
        assertThat(ok.get("deviceToken").isNull()).isTrue();            // not remembered unless asked
        assertThat(ok.get("rememberedDevice").asBoolean()).isFalse();
    }

    @Test
    void rememberedDeviceSignsInWithPasswordOnlyFor30Days() throws Exception {
        Account a = newStudent("remember");
        JsonNode first = loginRaw(a.username(), PASSWORD, code(a), true, null, 200);
        String deviceToken = first.get("deviceToken").asText();
        assertThat(deviceToken).hasSize(64);
        Instant until = Instant.parse(first.get("trustedUntil").asText());
        assertThat(Duration.between(Instant.now(), until).toDays()).isBetween(29L, 30L);

        // only a hash is stored
        assertThat(jdbc.queryForObject("select count(*) from trusted_devices where token_hash = ?", Integer.class,
                deviceToken)).isZero();
        assertThat(trusted.countByUserId(a.id())).isEqualTo(1);

        JsonNode second = loginRaw(a.username(), PASSWORD, null, null, deviceToken, 200);   // no code
        assertThat(second.get("token").asText()).isNotBlank();
        assertThat(second.get("rememberedDevice").asBoolean()).isTrue();
        assertThat(second.get("deviceToken").isNull()).isTrue();          // the 30 days are not extended

        // the password is still required
        loginRaw(a.username(), "wrong-password", null, null, deviceToken, 401);
    }

    @Test
    void aDeviceTokenOnlyWorksForItsOwnUser() throws Exception {
        Account a = newStudent("owner");
        Account b = newStudent("other");
        String deviceToken = loginRaw(a.username(), PASSWORD, code(a), true, null, 200).get("deviceToken").asText();
        loginRaw(b.username(), PASSWORD, null, null, deviceToken, 401);
        loginRaw(a.username(), PASSWORD, null, null, "not-a-real-token", 401);
    }

    @Test
    void afterThirtyDaysTheCodeIsRequiredAgain() throws Exception {
        Account a = newStudent("expired");
        String deviceToken = loginRaw(a.username(), PASSWORD, code(a), true, null, 200).get("deviceToken").asText();
        jdbc.update("update trusted_devices set expires_at = ? where user_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), a.id());
        loginRaw(a.username(), PASSWORD, null, null, deviceToken, 401);
        loginRaw(a.username(), PASSWORD, code(a), null, deviceToken, 200);   // with the code it works again
    }

    @Test
    void changingThePasswordForgetsRememberedDevices() throws Exception {
        Account a = newStudent("pwchange");
        String deviceToken = loginRaw(a.username(), PASSWORD, code(a), true, null, 200).get("deviceToken").asText();
        PUT("/api/profile/password", a, Map.of("oldPassword", PASSWORD, "newPassword", "NewPassword456!"), 200);
        assertThat(trusted.countByUserId(a.id())).isZero();
        loginRaw(a.username(), "NewPassword456!", null, null, deviceToken, 401);
    }
}
