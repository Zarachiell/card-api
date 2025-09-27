package com.example.api.service;

import com.example.api.config.properties.CryptoProperties;
import com.example.api.service.interfaces.CryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class CryptoServiceImpl implements CryptoService {
    private final CryptoProperties props;
    private final SecureRandom rng = new SecureRandom();

    private SecretKey aesKey() {
        byte[] k = hex(props.aesKeyHex());
        if (k.length != 32) throw new IllegalStateException("cards.crypto.aesKeyHex must be 32 bytes (64 hex)");
        return new SecretKeySpec(k, "AES");
    }

    private SecretKey hmacKey() {
        byte[] k = hex(props.hmacKeyHex());
        if (k.length < 16) throw new IllegalStateException("cards.crypto.hmacKeyHex too short");
        return new SecretKeySpec(k, "HmacSHA256");
    }

    public String encryptUtf8(String plain) {
        try {
            byte[] iv = new byte[12]; rng.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] packed = ByteBuffer.allocate(12 + ct.length).put(iv).put(ct).array();
            return java.util.Base64.getEncoder().encodeToString(packed);
        } catch (IllegalStateException e) { // validações de chave/hex aqui
            throw new IllegalStateException("encrypt_failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt_failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public String macHex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey());
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHex(out);
        } catch (IllegalStateException e) { // validação de hmacKey/hex
            throw new IllegalStateException("hmac_failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("hmac_failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static String toHex(byte[] b){
        StringBuilder sb = new StringBuilder(b.length*2);
        for (byte x: b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
    private static byte[] hex(String s){
        if (s == null) throw new IllegalStateException("missing key");
        int len = s.length();
        if ((len & 1) == 1) throw new IllegalStateException("hex odd length");
        byte[] out = new byte[len/2];
        for (int i=0;i<len;i+=2) out[i/2] = (byte)Integer.parseInt(s.substring(i,i+2),16);
        return out;
    }
}