package cometfit.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** TOTP used for MFA (NFR Security). Checked against the published RFC 6238 SHA-1 test vectors (last 6 digits). */
class TotpTest {
    private static final String RFC_SECRET = Totp.base32Encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesRfc6238Vectors() {
        assertThat(Totp.codeAt(RFC_SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(Totp.codeAt(RFC_SECRET, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
        assertThat(Totp.codeAt(RFC_SECRET, Instant.ofEpochSecond(1234567890))).isEqualTo("005924");
    }

    @Test
    void verifyAcceptsOneStepOfDriftOnly() {
        Instant t = Instant.ofEpochSecond(1_700_000_000L);
        String code = Totp.codeAt(RFC_SECRET, t);
        assertThat(Totp.verify(RFC_SECRET, code, t)).isTrue();
        assertThat(Totp.verify(RFC_SECRET, code, t.plusSeconds(30))).isTrue();
        assertThat(Totp.verify(RFC_SECRET, code, t.plusSeconds(120))).isFalse();
        assertThat(Totp.verify(RFC_SECRET, "12345", t)).isFalse();
        assertThat(Totp.verify(RFC_SECRET, null, t)).isFalse();
    }

    @Test
    void base32RoundTrip() {
        byte[] data = {0, 1, 2, (byte) 250, 127, 64};
        assertThat(Totp.base32Decode(Totp.base32Encode(data))).containsExactly(data);
        assertThat(Totp.newSecret()).matches("[A-Z2-7]{32}");
    }
}
