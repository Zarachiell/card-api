// src/main/java/com/example/api/config/CryptoConfig.java
package com.example.api.config;

import com.google.crypto.tink.*;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AesGcmKeyManager;
import com.google.crypto.tink.mac.HmacKeyManager;
import com.google.crypto.tink.mac.MacConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@Slf4j
public class CryptoConfig {

    static {
        try { AeadConfig.register(); MacConfig.register(); }
        catch (Exception e) { throw new RuntimeException("Tink register failed", e); }
    }

    @Bean
    public Aead aead(@Value("${crypto.tink.aead.keyset:}") String aeadKeysetB64) throws Exception {
        KeysetHandle handle = loadOrGenerateKeyset(aeadKeysetB64, AesGcmKeyManager.aes256GcmTemplate(), "AEAD");
        return handle.getPrimitive(Aead.class);
    }

    @Bean
    public com.google.crypto.tink.Mac mac(@Value("${crypto.tink.mac.keyset:}") String macKeysetB64) throws Exception {
        KeysetHandle handle = loadOrGenerateKeyset(macKeysetB64, HmacKeyManager.hmacSha256HalfDigestTemplate(), "MAC");
        return handle.getPrimitive(com.google.crypto.tink.Mac.class);
    }

    private KeysetHandle loadOrGenerateKeyset(String b64, KeyTemplate tmpl, String label) throws Exception {
        if (b64 != null && !b64.isBlank()) {
            byte[] json = Base64.getDecoder().decode(b64.getBytes(StandardCharsets.US_ASCII));
            return CleartextKeysetHandle.read(JsonKeysetReader.withBytes(json));
        }
        // Dev fallback (gera no arranque): em prod, passe via secret/KMS!
        return KeysetHandle.generateNew(tmpl);
    }
}
