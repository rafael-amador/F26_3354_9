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
 * Updated for TD-5: the checkbox is on the "Verify it's you" step (/verify); a remembered device skips that step.
 */
class RememberDeviceTest extends ApiTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired TrustedDeviceRepository trusted;

    /** TD-5 step 1: username + password (+ remembered device token). */
    private JsonNode signIn(String username, String password, String deviceToken, int status) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("username", username);
        b.put("password", password);
        if (deviceToken != null) b.put("deviceToken", deviceToken);
        return call(post("/api/auth/login"), null, b, status);
    }

    /** TD-5 step 2: the code (+ "Remember this device for 30 days"). */
    private JsonNode verify(String challengeId, String code, Boolean remember, int status) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("challengeId", challengeId);
        if (code != null) b.put("totpCode", code);
        if (remember != null) b.put("rememberDevice", remember);
        return call(post("/api/auth/verify"), null, b, status);
    }

    private String code(Account a) { return Totp.codeAt(a.secret(), Instant.now()); }

    /** Full sign-in on a new device with the checkbox ticked; returns the /verify response. */
    private JsonNode signInAndRemember(Account a) throws Exception {
        JsonNode step1 = signIn(a.username(), PASSWORD, null, 200);
        assertThat(step1.get("verificationRequired").asBoolean()).isTrue();
        return verify(step1.get("challengeId").asText(), code(a), true, 200);
    }

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
        JsonNode step1 = signIn(a.username(), PASSWORD, null, 200);
        assertThat(step1.get("verificationRequired").asBoolean()).isTrue();
        assertThat(step1.get("token").isNull()).isTrue();
        signIn(a.username(), "wrong-password", null, 401);
        JsonNode ok = verify(step1.get("challengeId").asText(), code(a), null, 200);
        assertThat(ok.get("deviceToken").isNull()).isTrue();            // not remembered unless asked
        assertThat(ok.get("rememberedDevice").asBoolean()).isFalse();
    }

    @Test
    void rememberedDeviceSignsInWithPasswordOnlyFor30Days() throws Exception {
        Account a = newStudent("remember");
        JsonNode first = signInAndRemember(a);
        String deviceToken = first.get("deviceToken").asText();
        assertThat(deviceToken).hasSize(64);
        Instant until = Instant.parse(first.get("trustedUntil").asText());
        assertThat(Duration.between(Instant.now(), until).toDays()).isBetween(29L, 30L);

        // only a hash is stored
        assertThat(jdbc.queryForObject("select count(*) from trusted_devices where token_hash = ?", Integer.class,
                deviceToken)).isZero();
        assertThat(trusted.countByUserId(a.id())).isEqualTo(1);

        JsonNode second = signIn(a.username(), PASSWORD, deviceToken, 200);   // TD-5 (2): no second step
        assertThat(second.get("verificationRequired").asBoolean()).isFalse();
        assertThat(second.get("token").asText()).isNotBlank();
        assertThat(second.get("rememberedDevice").asBoolean()).isTrue();
        assertThat(second.get("deviceToken").isNull()).isTrue();          // the 30 days are not extended

        // the password is still required
        signIn(a.username(), "wrong-password", deviceToken, 401);
    }

    @Test
    void aDeviceTokenOnlyWorksForItsOwnUser() throws Exception {
        Account a = newStudent("owner");
        Account b = newStudent("other");
        String deviceToken = signInAndRemember(a).get("deviceToken").asText();
        // another user's device token does not skip the code: step 2 is still required
        assertThat(signIn(b.username(), PASSWORD, deviceToken, 200).get("verificationRequired").asBoolean()).isTrue();
        assertThat(signIn(a.username(), PASSWORD, "not-a-real-token", 200).get("verificationRequired").asBoolean()).isTrue();
    }

    @Test
    void afterThirtyDaysTheCodeIsRequiredAgain() throws Exception {
        Account a = newStudent("expired");
        String deviceToken = signInAndRemember(a).get("deviceToken").asText();
        jdbc.update("update trusted_devices set expires_at = ? where user_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), a.id());
        JsonNode step1 = signIn(a.username(), PASSWORD, deviceToken, 200);
        assertThat(step1.get("verificationRequired").asBoolean()).isTrue();
        verify(step1.get("challengeId").asText(), code(a), null, 200);   // with the code it works again
    }

    @Test
    void changingThePasswordForgetsRememberedDevices() throws Exception {
        Account a = newStudent("pwchange");
        String deviceToken = signInAndRemember(a).get("deviceToken").asText();
        PUT("/api/profile/password", a, Map.of("oldPassword", PASSWORD, "newPassword", "NewPassword456!"), 200);
        assertThat(trusted.countByUserId(a.id())).isZero();
        assertThat(signIn(a.username(), "NewPassword456!", deviceToken, 200).get("verificationRequired").asBoolean())
                .isTrue();
    }
}
