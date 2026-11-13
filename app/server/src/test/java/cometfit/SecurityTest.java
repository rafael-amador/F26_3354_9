package cometfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import cometfit.security.Totp;

/**
 * NFR Security (Deliverable 1 §4.2): "username and password authentication as well as TOTP for MFA. Interactions
 * ... authorized via a backend permissions list, and all user data will be encrypted". Fig 4.3 "Log In (Password +
 * TOTP)" included by UC-1..UC-4.
 */
class SecurityTest extends ApiTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test
    void loginRequiresPasswordAndValidTotp() throws Exception {
        Account a = newStudent("sec");
        String good = Totp.codeAt(a.secret(), Instant.now());
        String bad = good.equals("000000") ? "111111" : "000000";
        call(post("/api/auth/login"), null, Map.of("username", a.username(), "password", PASSWORD, "totpCode", bad), 401);
        call(post("/api/auth/login"), null, Map.of("username", a.username(), "password", "wrong-pass", "totpCode", good), 401);
        call(post("/api/auth/login"), null, Map.of("username", a.username(), "password", PASSWORD), 400);
        assertThat(login(a.username(), PASSWORD, a.secret())).hasSize(64);
    }

    @Test
    void protectedEndpointsNeedASession() throws Exception {
        call(get("/api/nutrition/summary"), null, null, 401);
        call(get("/api/nutrition/summary"), "not-a-real-token", null, 401);
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        Account a = newStudent("out");
        GET("/api/auth/me", a, 200);
        POST("/api/auth/logout", a, null, 204);
        GET("/api/auth/me", a, 401);
    }

    @Test
    void permissionsListSeparatesStudentsAndDevelopers() throws Exception {
        Account student = newStudent("perm");
        Account dev = developer();
        GET("/api/admin/uptime", student, 403);
        POST("/api/admin/modules/SOCIAL/disable", student, null, 403);
        GET("/api/nutrition/summary", dev, 403);   // developers have no student permissions
        GET("/api/admin/uptime", dev, 200);
    }

    @Test
    void emailAndTotpSecretAreEncryptedAtRest() throws Exception {
        Account a = newStudent("enc");
        Map<String, Object> row = jdbc.queryForMap(
                "select email_enc, totp_secret_enc, password_hash from users where id = ?", a.id());
        assertThat((String) row.get("email_enc")).doesNotContain(a.username()).doesNotContain("@");
        assertThat((String) row.get("totp_secret_enc")).isNotEqualTo(a.secret());
        assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain(PASSWORD);
        // and still readable through the application
        assertThat(GET("/api/profile", a, 200).get("email").asText()).isEqualTo(a.username() + "@test.local");
    }

    @Test
    void registrationValidatesCredentials() throws Exception {
        call(post("/api/auth/register"), null, Map.of("username", "x", "password", "Password123!", "email", "a@b.co"), 400);
        call(post("/api/auth/register"), null, Map.of("username", "validname", "password", "short", "email", "a@b.co"), 400);
        call(post("/api/auth/register"), null, Map.of("username", "alice", "password", "Password123!", "email", "a@b.co"), 409);
    }

    @Test
    void changePasswordChecksTheOldPassword() throws Exception {
        Account a = newStudent("pw");
        PUT("/api/profile/password", a, Map.of("oldPassword", "nope-nope", "newPassword", "NewPassword1"), 400);
        PUT("/api/profile/password", a, Map.of("oldPassword", PASSWORD, "newPassword", "NewPassword1"), 200);
        assertThat(login(a.username(), "NewPassword1", a.secret())).isNotBlank();
    }
}
