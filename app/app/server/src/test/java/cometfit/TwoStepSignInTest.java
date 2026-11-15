package cometfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import cometfit.security.AuthService;
import cometfit.security.LoginChallengeRepository;
import cometfit.security.Totp;

/**
 * TD-5 (5): "Server and client tests must cover: password-only login without a remembered device does not give a
 * session; remembered device skips step 2; wrong code fails; challenge expires and is single-use." Plus TD-5 (3)
 * registration -> setup challenge -> first code signs in, and TD-5 (5)/A-31 one generic failure message.
 */
class TwoStepSignInTest extends ApiTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoginChallengeRepository challenges;

    private JsonNode passwordStep(String username, String password, String deviceToken, int status) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("username", username);
        b.put("password", password);
        if (deviceToken != null) b.put("deviceToken", deviceToken);
        return call(post("/api/auth/login"), null, b, status);
    }

    private JsonNode verify(String challengeId, String code, boolean remember, int status) throws Exception {
        return call(post("/api/auth/verify"), null,
                Map.of("challengeId", challengeId, "totpCode", code, "rememberDevice", remember), status);
    }

    private static String wrong(String good) { return good.equals("000000") ? "111111" : "000000"; }

    @Test
    void passwordOnlyWithoutRememberedDeviceGivesNoSession() throws Exception {
        Account a = newStudent("td5pw");
        JsonNode r = passwordStep(a.username(), PASSWORD, null, 200);
        assertThat(r.get("verificationRequired").asBoolean()).isTrue();
        assertThat(r.get("token").isNull()).isTrue();
        assertThat(r.get("user").isNull()).isTrue();
        String challengeId = r.get("challengeId").asText();
        assertThat(challengeId).hasSize(64);
        // the challenge id is not a session token
        call(get("/api/auth/me"), challengeId, null, 401);
        call(get("/api/nutrition/summary"), challengeId, null, 401);
        // only a hash of the challenge is stored
        assertThat(jdbc.queryForObject("select count(*) from login_challenges where token_hash = ?", Integer.class,
                challengeId)).isZero();
    }

    @Test
    void rememberedDeviceSkipsStepTwo() throws Exception {
        Account a = newStudent("td5rem");
        String ch = passwordStep(a.username(), PASSWORD, null, 200).get("challengeId").asText();
        String deviceToken = verify(ch, Totp.codeAt(a.secret(), Instant.now()), true, 200).get("deviceToken").asText();
        long before = challenges.countByUserId(a.id());
        JsonNode r = passwordStep(a.username(), PASSWORD, deviceToken, 200);
        assertThat(r.get("verificationRequired").asBoolean()).isFalse();
        assertThat(r.get("challengeId").isNull()).isTrue();
        assertThat(r.get("rememberedDevice").asBoolean()).isTrue();
        assertThat(challenges.countByUserId(a.id())).isEqualTo(before);   // no challenge was created
        GET("/api/auth/me", new Account(a.id(), a.username(), a.secret(), r.get("token").asText()), 200);
    }

    @Test
    void wrongCodeFailsWithTheGenericMessage() throws Exception {
        Account a = newStudent("td5wrong");
        String ch = passwordStep(a.username(), PASSWORD, null, 200).get("challengeId").asText();
        JsonNode bad = verify(ch, wrong(Totp.codeAt(a.secret(), Instant.now())), false, 401);
        assertThat(bad.get("message").asText()).isEqualTo(AuthService.GENERIC_FAILURE);
        JsonNode badPw = passwordStep(a.username(), "wrong-password", null, 401);
        JsonNode noUser = passwordStep("no_such_user_td5", PASSWORD, null, 401);
        assertThat(badPw.get("message").asText()).isEqualTo(AuthService.GENERIC_FAILURE);
        assertThat(noUser.get("message").asText()).isEqualTo(AuthService.GENERIC_FAILURE);
        // a wrong code does not burn the challenge: the right code still works
        verify(ch, Totp.codeAt(a.secret(), Instant.now()), false, 200);
    }

    @Test
    void tooManyWrongCodesDropTheChallenge() throws Exception {
        Account a = newStudent("td5many");
        String ch = passwordStep(a.username(), PASSWORD, null, 200).get("challengeId").asText();
        String bad = wrong(Totp.codeAt(a.secret(), Instant.now()));
        for (int i = 0; i < AuthService.MAX_CODE_ATTEMPTS; i++) verify(ch, bad, false, 401);
        verify(ch, Totp.codeAt(a.secret(), Instant.now()), false, 401);
    }

    @Test
    void challengeIsSingleUse() throws Exception {
        Account a = newStudent("td5once");
        String ch = passwordStep(a.username(), PASSWORD, null, 200).get("challengeId").asText();
        String code = Totp.codeAt(a.secret(), Instant.now());
        verify(ch, code, false, 200);
        JsonNode again = verify(ch, code, false, 401);
        assertThat(again.get("message").asText()).isEqualTo(AuthService.GENERIC_FAILURE);
    }

    @Test
    void challengeExpires() throws Exception {
        Account a = newStudent("td5exp");
        String ch = passwordStep(a.username(), PASSWORD, null, 200).get("challengeId").asText();
        jdbc.update("update login_challenges set expires_at = ? where user_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), a.id());
        verify(ch, Totp.codeAt(a.secret(), Instant.now()), false, 401);
    }

    @Test
    void unknownChallengeFails() throws Exception {
        verify("f".repeat(64), "123456", false, 401);
    }

    @Test
    void registrationEndsWithASetupChallengeThatTheFirstCodeCompletes() throws Exception {
        String username = "td5reg_" + System.nanoTime() % 100000;
        JsonNode reg = call(post("/api/auth/register"), null, Map.of("username", username, "password", PASSWORD,
                "email", username + "@test.local"), 201);
        assertThat(reg.has("token")).isFalse();                      // Register alone gives no session
        String setup = reg.get("setupChallengeId").asText();
        String secret = reg.get("totpSecret").asText();
        verify(setup, wrong(Totp.codeAt(secret, Instant.now())), false, 401);
        JsonNode done = verify(setup, Totp.codeAt(secret, Instant.now()), true, 200);
        assertThat(done.get("token").asText()).hasSize(64);
        assertThat(done.get("user").get("username").asText()).isEqualTo(username);
        assertThat(done.get("deviceToken").asText()).hasSize(64);   // remember-device also works at setup
        // and that device is now remembered for the next sign-in
        assertThat(passwordStep(username, PASSWORD, done.get("deviceToken").asText(), 200)
                .get("verificationRequired").asBoolean()).isFalse();
    }
}
