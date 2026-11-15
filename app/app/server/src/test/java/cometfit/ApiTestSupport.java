package cometfit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cometfit.security.Totp;

/** Shared helpers for the API-level tests: register a student, log in with password + TOTP, call the REST API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {
    private static final AtomicInteger SEQ = new AtomicInteger();
    public static final String PASSWORD = "Password123!";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    /** A logged-in test user. */
    public record Account(long id, String username, String secret, String token) {
        String bearer() { return "Bearer " + token; }
    }

    protected Account newStudent(String prefix) throws Exception {
        String username = prefix + SEQ.incrementAndGet() + "_" + (System.nanoTime() % 100000);
        JsonNode reg = body(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                        "username", username, "password", PASSWORD, "email", username + "@test.local",
                        "displayName", "Name " + username))))
                .andReturn(), 201);
        String secret = reg.get("totpSecret").asText();
        return new Account(reg.get("user").get("userId").asLong(), username, secret, login(username, PASSWORD, secret));
    }

    protected Account developer() throws Exception {
        String token = login("dev", DemoDataSeeder.DEMO_PASSWORD, DemoDataSeeder.DEV_SECRET);
        JsonNode me = call(get("/api/auth/me"), token, null, 200);
        return new Account(me.get("userId").asLong(), "dev", DemoDataSeeder.DEV_SECRET, token);
    }

    /** TD-5: password step (returns a challenge, no token), then the 6-digit code on /verify. */
    protected String login(String username, String password, String secret) throws Exception {
        JsonNode step1 = body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                        "username", username, "password", password)))).andReturn(), 200);
        if (!step1.get("verificationRequired").asBoolean()) throw new AssertionError("expected a challenge: " + step1);
        String code = Totp.codeAt(secret, Instant.now());
        JsonNode r = body(mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                        "challengeId", step1.get("challengeId").asText(), "totpCode", code)))).andReturn(), 200);
        return r.get("token").asText();
    }

    protected JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expectStatus) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return body(mvc.perform(req).andReturn(), expectStatus);
    }

    protected JsonNode GET(String url, Account a, int status) throws Exception { return call(get(url), a.token(), null, status); }
    protected JsonNode POST(String url, Account a, Object body, int status) throws Exception { return call(post(url), a.token(), body, status); }
    protected JsonNode PUT(String url, Account a, Object body, int status) throws Exception { return call(put(url), a.token(), body, status); }
    protected JsonNode DELETE(String url, Account a, int status) throws Exception { return call(delete(url), a.token(), null, status); }

    protected JsonNode body(MvcResult r, int expectStatus) throws Exception {
        String content = r.getResponse().getContentAsString();
        if (r.getResponse().getStatus() != expectStatus) {
            throw new AssertionError("Expected HTTP " + expectStatus + " but got " + r.getResponse().getStatus()
                    + " for " + r.getRequest().getMethod() + " " + r.getRequest().getRequestURI() + ": " + content);
        }
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }
}
