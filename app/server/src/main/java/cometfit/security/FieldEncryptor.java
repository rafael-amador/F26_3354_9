package cometfit.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM field encryption for sensitive user data at rest.
 * NFR Security: "all user data will be encrypted and stored by the backend";
 * NFR Legislative-Security: "reasonable security safeguards for stored ... user data";
 * §5.3.2 "Encrypted Database". Scope of field-level encryption: A-5.
 */
@Component
public class FieldEncryptor {
    private static final SecureRandom RNG = new SecureRandom();
    private static volatile FieldEncryptor instance;

    private final SecretKeySpec key;

    public FieldEncryptor(@Value("${cometfit.data-key}") String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) throw new IllegalStateException("cometfit.data-key must be 32 bytes (Base64)");
        this.key = new SecretKeySpec(raw, "AES");
        instance = this;
    }

    static FieldEncryptor get() {
        if (instance == null) throw new IllegalStateException("FieldEncryptor not initialised");
        return instance;
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not decrypt field (wrong COMETFIT_DATA_KEY?)", e);
        }
    }
}
